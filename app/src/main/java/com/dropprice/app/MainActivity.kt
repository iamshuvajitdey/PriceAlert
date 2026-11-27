package com.dropprice.app

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowInsets
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.net.URI
import javax.net.ssl.HttpsURLConnection
import kotlin.concurrent.thread

class MainActivity : Activity() {
    private val storage by lazy { getSharedPreferences("watchlist", MODE_PRIVATE) }
    private val products by lazy { JSONArray(storage.getString("products", "[]")) }
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val surface = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(241, 247, 244))
            setOnApplyWindowInsetsListener { view, insets ->
                @Suppress("DEPRECATION")
                view.setPadding(
                    insets.systemWindowInsetLeft + dp(20),
                    insets.systemWindowInsetTop + dp(16),
                    insets.systemWindowInsetRight + dp(20),
                    insets.systemWindowInsetBottom + dp(16)
                )
                WindowInsets.CONSUMED
            }
        }
        surface.addView(label("Drop Price", 32f))
        surface.addView(button("Add a store link") { editLink() })
        list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        surface.addView(ScrollView(this).apply { addView(list) })
        setContentView(surface)
        render()
        if (savedInstanceState == null && intent.action == Intent.ACTION_SEND) {
            val shared = intent.getStringExtra(Intent.EXTRA_TEXT).orEmpty()
            editLink(Regex("""https://[^\s<>]+""", RegexOption.IGNORE_CASE).find(shared)?.value.orEmpty())
        }
    }

    private fun render() {
        list.removeAllViews()
        if (products.length() == 0) list.addView(label("Add a product to start tracking store offers.", 16f))
        for (index in 0 until products.length()) {
            val product = products.getJSONObject(index)
            list.addView(label(product.getString("name"), 22f))
            val offers = product.getJSONArray("offers")
            for (offerIndex in 0 until offers.length()) {
                val url = offers.getString(offerIndex)
                val status = label("", 15f)
                list.addView(button("Check " + URI(url).host) { check(url, status) })
                list.addView(status)
            }
            list.addView(button("Add another offer") { editLink(product = product) })
        }
    }

    private fun editLink(prefill: String = "", product: JSONObject? = null) {
        val form = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), 0)
        }
        val name = EditText(this).apply {
            hint = "Product name"
            setSingleLine()
            setText(product?.getString("name").orEmpty())
        }
        val link = EditText(this).apply {
            hint = "HTTPS store URL"
            setSingleLine()
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_VARIATION_URI
            setText(prefill)
        }
        if (product == null) form.addView(name)
        form.addView(link)
        if (product == null && products.length() > 0) {
            form.addView(button("Choose an existing product") {
                val names = Array(products.length()) { products.getJSONObject(it).getString("name") }
                val url = link.text.toString()
                formDialog?.dismiss()
                AlertDialog.Builder(this).setTitle("Add offer to")
                    .setItems(names) { _, index -> editLink(url, products.getJSONObject(index)) }
                    .show()
            })
        }
        val dialog = AlertDialog.Builder(this).setTitle("Add store link").setView(form)
            .setNegativeButton("Cancel", null).setPositiveButton("Save", null).create()
        formDialog = dialog
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val url = link.text.toString().trim()
                val uri = runCatching { URI(url) }.getOrNull()
                if (uri == null || !uri.scheme.equals("https", ignoreCase = true) ||
                    uri.host.isNullOrBlank() || uri.userInfo != null) {
                    link.error = "Enter a valid HTTPS store URL"
                    return@setOnClickListener
                }
                val title = name.text.toString().trim()
                if (title.isBlank()) {
                    name.error = "Enter a product name"
                    return@setOnClickListener
                }
                val item = product ?: JSONObject().put("name", title).put("offers", JSONArray())
                val offers = item.getJSONArray("offers")
                if ((0 until offers.length()).any { offers.getString(it) == url }) {
                    link.error = "This offer is already saved"
                    return@setOnClickListener
                }
                if (product == null) products.put(item)
                offers.put(url)
                storage.edit().putString("products", products.toString()).apply()
                dialog.dismiss()
                render()
            }
        }
        dialog.show()
    }

    private var formDialog: AlertDialog? = null

    private fun check(url: String, status: TextView) {
        status.text = "Checking…"
        thread {
            val result = runCatching { StructuredPrice.check(url) }
                .getOrElse { "Couldn’t check this store page." }
            runOnUiThread { if (!isDestroyed) status.text = result }
        }
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun label(value: String, size: Float) = TextView(this).apply {
        text = value
        textSize = size
        setTextColor(Color.rgb(23, 49, 39))
        setPadding(0, dp(8), 0, dp(8))
    }

    private fun button(value: String, action: () -> Unit) = Button(this).apply {
        text = value
        isAllCaps = false
        setOnClickListener { action() }
    }
}

private object StructuredPrice {
    private val scripts = Regex(
        """<script\b[^>]*\btype\s*=\s*["']application/ld\+json["'][^>]*>([\s\S]*?)</script\s*>""",
        RegexOption.IGNORE_CASE
    )

    fun check(url: String): String {
        val connection = URI(url).toURL().openConnection() as HttpsURLConnection
        val html = try {
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.instanceFollowRedirects = false
            check(connection.responseCode == 200)
            connection.inputStream.bufferedReader().use { reader ->
                val buffer = CharArray(8192)
                buildString {
                    while (length < 2_000_000) {
                        val count = reader.read(buffer, 0, minOf(buffer.size, 2_000_000 - length))
                        if (count < 0) break
                        append(buffer, 0, count)
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
        val offers = scripts.findAll(html)
            .mapNotNull { runCatching { JSONTokener(it.groupValues[1]).nextValue() }.getOrNull() }
            .flatMap { products(it) }
            .flatMap { product ->
                when (val value = product.opt("offers")) {
                    is JSONObject -> sequenceOf(value)
                    is JSONArray -> (0 until value.length()).asSequence().mapNotNull { value.optJSONObject(it) }
                    else -> emptySequence()
                }
            }
            .toList()
        val offer = offers.singleOrNull() ?: return "No single price found on this page."
        val price = offer.optString("price").toBigDecimalOrNull()?.takeIf { it.signum() >= 0 }
        val currency = offer.optString("priceCurrency")
        if (price == null || !currency.matches(Regex("[A-Z]{3}"))) return "No usable price found."
        val availability = when (offer.optString("availability").substringAfterLast('/')) {
            "InStock" -> "In stock"
            "OutOfStock", "SoldOut" -> "Out of stock"
            else -> "Stock unknown"
        }
        return "$currency $price · $availability"
    }

    private fun products(value: Any?): Sequence<JSONObject> = when (value) {
        is JSONObject -> {
            val type = value.opt("@type")
            val isProduct = type == "Product" ||
                (type is JSONArray && (0 until type.length()).any { type.optString(it) == "Product" })
            (if (isProduct) sequenceOf(value) else emptySequence()) + products(value.opt("@graph"))
        }
        is JSONArray -> (0 until value.length()).asSequence().flatMap { products(value.opt(it)) }
        else -> emptySequence()
    }
}
