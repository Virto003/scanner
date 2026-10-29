package com.vuteq.scank

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import java.lang.ref.WeakReference
import kotlin.math.abs

class FloatingScannerService : AccessibilityService() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var layoutParams: WindowManager.LayoutParams? = null
    private var lastFocusedNode: AccessibilityNodeInfo? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        setTheme(R.style.Theme_ScannerKeyboard)
        instanceRef = WeakReference(this)
        mostrarBotaoFlutuante()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        val source = event.source ?: return
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED ||
            event.eventType == AccessibilityEvent.TYPE_VIEW_TEXT_SELECTION_CHANGED
        ) {
            if (source.isEditable) {
                lastFocusedNode = source
            }
        }
    }

    override fun onInterrupt() {
        // Não utilizado
    }

    @SuppressLint("ClickableViewAccessibility", "InflateParams")
    fun mostrarBotaoFlutuante() {
        if (!Settings.canDrawOverlays(this)) return
        if (floatingView != null) return

        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val contextThemeWrapper = ContextThemeWrapper(this, R.style.Theme_ScannerKeyboard)
        val inflater = LayoutInflater.from(contextThemeWrapper)
        floatingView = inflater.inflate(R.layout.floating_button_layout, null)

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 400
        }

        val fab = floatingView?.findViewById<View>(R.id.fabScanner)

        fab?.setOnTouchListener(
            object : View.OnTouchListener {
                private var initialX = 0
                private var initialY = 0
                private var initialTouchX = 0f
                private var initialTouchY = 0f

                override fun onTouch(v: View?, event: MotionEvent): Boolean {
                    when (event.action) {
                        MotionEvent.ACTION_DOWN -> {
                            initialX = layoutParams?.x ?: 0
                            initialY = layoutParams?.y ?: 0
                            initialTouchX = event.rawX
                            initialTouchY = event.rawY
                            return true
                        }
                        MotionEvent.ACTION_MOVE -> {
                            layoutParams?.x = initialX + (event.rawX - initialTouchX).toInt()
                            layoutParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                            windowManager?.updateViewLayout(floatingView, layoutParams)
                            return true
                        }
                        MotionEvent.ACTION_UP -> {
                            val diffX = abs(event.rawX - initialTouchX)
                            val diffY = abs(event.rawY - initialTouchY)
                            if ((diffX < 10) && (diffY < 10)) {
                                abrirScanner()
                            }
                            return true
                        }
                    }
                    return false
                }
            },
        )

        try {
            windowManager?.addView(floatingView, layoutParams)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun abrirScanner() {
        val intent = Intent(this, ScannerActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)
    }

    fun injetarTextoNoCampoFocado(texto: String) {
        // Garantia de ter o texto na área de transferência (Clipboard)
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Barcode", texto)
        clipboard.setPrimaryClip(clip)

        val handler = Handler(Looper.getMainLooper())
        var tentados = 0
        val maxTentativas = 5

        val runnable = object : Runnable {
            override fun run() {
                tentados++
                val targetNode = obterNoEditavelFocado()

                if (targetNode != null) {
                    val inserido = tentarInjetarTexto(targetNode, texto)
                    if (inserido) {
                        Toast.makeText(
                            this@FloatingScannerService,
                            getString(R.string.codigo_inserido, texto),
                            Toast.LENGTH_SHORT,
                        ).show()
                        return
                    }
                }

                if (tentados < maxTentativas) {
                    handler.postDelayed(this, 150)
                } else {
                    Toast.makeText(
                        this@FloatingScannerService,
                        getString(R.string.codigo_copiado, texto),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }

        // Pequeno atraso para permitir que a tela do Scanner feche completamente e o app anterior retome o foco
        handler.postDelayed(runnable, 200)
    }

    private fun obterNoEditavelFocado(): AccessibilityNodeInfo? {
        // 1. Tenta o nó capturado recentemente pelo evento de acessibilidade
        lastFocusedNode?.let { node ->
            if (node.refresh() && node.isEditable) {
                return node
            }
        }

        // 2. Tenta encontrar o foco na janela ativa atual
        val root = rootInActiveWindow
        if (root != null) {
            val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            if (focused != null && focused.isEditable) {
                return focused
            }
            val deBusca = buscarEditavelEmFoco(root)
            if (deBusca != null) {
                return deBusca
            }
        }

        // 3. Tenta varrer todas as janelas interativas
        try {
            val todasJanelas = windows
            for (window in todasJanelas) {
                val winRoot = window.root ?: continue
                val focused = winRoot.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
                if ((focused != null) && focused.isEditable) {
                    return focused
                }
                val deBusca = buscarEditavelEmFoco(winRoot)
                if (deBusca != null) {
                    return deBusca
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        return null
    }

    private fun buscarEditavelEmFoco(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isEditable && (node.isFocused || node.isAccessibilityFocused)) {
            return node
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i) ?: continue
            val resultado = buscarEditavelEmFoco(child)
            if (resultado != null) {
                return resultado
            }
        }
        if (node.isEditable) {
            return node
        }
        return null
    }

    private fun tentarInjetarTexto(targetNode: AccessibilityNodeInfo, texto: String): Boolean {
        // Estratégia A: Ação direta ACTION_SET_TEXT
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, texto)
        }
        var sucesso = targetNode.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

        // Estratégia B: Se ACTION_SET_TEXT falhou (comum em WebViews/Campos customizados), tenta ACTION_PASTE
        if (!sucesso) {
            sucesso = targetNode.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }

        return sucesso
    }

    override fun onDestroy() {
        super.onDestroy()
        if (floatingView != null) {
            try {
                windowManager?.removeView(floatingView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            floatingView = null
        }
        instanceRef = null
    }

    companion object {
        private var instanceRef: WeakReference<FloatingScannerService>? = null

        val instance: FloatingScannerService?
            get() = instanceRef?.get()
    }
}
