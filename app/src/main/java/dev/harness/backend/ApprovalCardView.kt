package dev.harness.backend

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import dev.harness.agent.ApprovalResponse

/**
 * The approval card as plain Views: it is drawn from the accessibility service, which has no Compose
 * lifecycle and no app theme. Big text and big buttons, because the person reading may not be technical.
 */
internal fun buildApprovalCard(
    context: Context,
    card: ApprovalCard,
    onResponse: (ApprovalResponse) -> Unit,
): View {
    val dark = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    val surface = if (dark) 0xFF1B1F23.toInt() else 0xFFF6F8F9.toInt()
    val ink = if (dark) 0xFFF1F3F4.toInt() else 0xFF111417.toInt()
    val subdued = if (dark) 0xFFA7B0B8.toInt() else 0xFF55606A.toInt()
    val well = if (dark) 0xFF2A3036.toInt() else 0xFFE4E9EC.toInt()
    val orange = if (dark) 0xFFFF8A4C.toInt() else 0xFFC9480A.toInt()

    fun dp(value: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics).toInt()

    fun text(value: String, sp: Float, color: Int, bold: Boolean = false, mono: Boolean = false) = TextView(context).apply {
        this.text = value
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = when {
            mono -> Typeface.MONOSPACE
            bold -> Typeface.DEFAULT_BOLD
            else -> Typeface.DEFAULT
        }
    }

    fun button(label: String, textColor: Int, background: Int, strokeColor: Int?, response: ApprovalResponse) = TextView(context).apply {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
        setTextColor(textColor)
        typeface = Typeface.DEFAULT_BOLD
        gravity = Gravity.CENTER
        minHeight = dp(56)
        setPadding(dp(12), dp(8), dp(12), dp(8))
        this.background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(background)
            if (strokeColor != null) setStroke(dp(2), strokeColor)
        }
        isClickable = true
        setOnClickListener { onResponse(response) }
    }

    fun gap(height: Int) = View(context).apply { layoutParams = LinearLayout.LayoutParams(1, dp(height)) }

    val column = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(20), dp(20), dp(20))
        background = GradientDrawable().apply {
            cornerRadius = dp(24).toFloat()
            setColor(surface)
            setStroke(dp(2), orange)
        }
        elevation = dp(8).toFloat()
    }

    column.addView(text("Harness is asking", 14f, orange, bold = true))
    column.addView(text(card.title, 24f, ink, bold = true))
    column.addView(text("Nothing happens until you allow it.", 16f, subdued))
    if (card.details.isNotBlank()) {
        column.addView(gap(12))
        column.addView(
            text(card.details.take(400), 15f, ink, mono = true).apply {
                maxLines = 6
                setPadding(dp(12), dp(10), dp(12), dp(10))
                background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(well) }
            },
        )
    }
    column.addView(gap(16))

    val row = LinearLayout(context).apply { orientation = LinearLayout.HORIZONTAL }
    fun half(view: View, startGap: Boolean) = row.addView(
        view,
        LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { if (startGap) marginStart = dp(12) },
    )
    // Deny and Allow get equal weight: neither is the obvious tap.
    half(button("Deny", ink, Color.TRANSPARENT, subdued, ApprovalResponse.DENY), startGap = false)
    half(button("Allow once", Color.WHITE, orange, null, ApprovalResponse.ALLOW_ONCE), startGap = true)
    column.addView(row)

    if (card.canAlwaysAllow) {
        column.addView(gap(12))
        column.addView(button("Always allow: ${card.title.lowercase()}", orange, Color.TRANSPARENT, orange, ApprovalResponse.ALWAYS_ALLOW))
        column.addView(gap(6))
        column.addView(text("Stops asking for this, in every chat. Undo it in Harness settings.", 13f, subdued))
    }
    return column
}
