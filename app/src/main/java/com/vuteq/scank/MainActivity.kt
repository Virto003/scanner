package com.vuteq.scank

import android.Manifest
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.view.accessibility.AccessibilityManager
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class MainActivity : AppCompatActivity() {

    private lateinit var txtStatusCamera: TextView
    private lateinit var txtStatusOverlay: TextView
    private lateinit var txtStatusAccessibility: TextView

    private lateinit var btnPermissaoCamera: Button
    private lateinit var btnPermissaoOverlay: Button
    private lateinit var btnPermissaoAccessibility: Button

    private val requestCameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { isGranted ->
        if (isGranted) {
            Toast.makeText(this, "Permissão da câmera concedida!", Toast.LENGTH_SHORT).show()
        }
        atualizarStatusPermissoes()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        txtStatusCamera = findViewById(R.id.txtStatusCamera)
        txtStatusOverlay = findViewById(R.id.txtStatusOverlay)
        txtStatusAccessibility = findViewById(R.id.txtStatusAccessibility)

        btnPermissaoCamera = findViewById(R.id.btnPermissaoCamera)
        btnPermissaoOverlay = findViewById(R.id.btnPermissaoOverlay)
        btnPermissaoAccessibility = findViewById(R.id.btnPermissaoAccessibility)

        btnPermissaoCamera.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        btnPermissaoOverlay.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    "package:$packageName".toUri(),
                )
                startActivity(intent)
            }
        }

        btnPermissaoAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        atualizarStatusPermissoes()
    }

    private fun atualizarStatusPermissoes() {
        // 1. Status Câmera
        val temCamera = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        if (temCamera) {
            txtStatusCamera.text = getString(R.string.status_concedido)
            txtStatusCamera.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnPermissaoCamera.isEnabled = false
        } else {
            txtStatusCamera.text = getString(R.string.status_pendente)
            txtStatusCamera.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnPermissaoCamera.isEnabled = true
        }

        // 2. Status Sobreposição de Tela
        val temOverlay = Settings.canDrawOverlays(this)
        if (temOverlay) {
            txtStatusOverlay.text = getString(R.string.status_concedido)
            txtStatusOverlay.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnPermissaoOverlay.isEnabled = false
        } else {
            txtStatusOverlay.text = getString(R.string.status_pendente)
            txtStatusOverlay.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnPermissaoOverlay.isEnabled = true
        }

        // 3. Status Acessibilidade
        val temAcessibilidade = isAccessibilityServiceEnabled()
        if (temAcessibilidade) {
            txtStatusAccessibility.text = getString(R.string.status_concedido)
            txtStatusAccessibility.setTextColor(ContextCompat.getColor(this, android.R.color.holo_green_dark))
            btnPermissaoAccessibility.isEnabled = false

            // Se a permissão de sobreposição também estiver concedida, garante que o botão flutuante está visível
            if (temOverlay) {
                FloatingScannerService.instance?.mostrarBotaoFlutuante()
            }
        } else {
            txtStatusAccessibility.text = getString(R.string.status_pendente)
            txtStatusAccessibility.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
            btnPermissaoAccessibility.isEnabled = true
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val am = (getSystemService(ACCESSIBILITY_SERVICE) as? AccessibilityManager) ?: return false
        val enabledServices = am.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_GENERIC)
        for (service in enabledServices) {
            if ((service.resolveInfo.serviceInfo.packageName == packageName) &&
                (service.resolveInfo.serviceInfo.name == FloatingScannerService::class.java.name)
            ) {
                return true
            }
        }
        return false
    }
}
