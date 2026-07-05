package com.example.autofish

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private lateinit var tvStep1: TextView
    private lateinit var tvStep2: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStep1 = findViewById(R.id.tvStep1)
        tvStep2 = findViewById(R.id.tvStep2)

        findViewById<Button>(R.id.btnAccessibility).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        findViewById<Button>(R.id.btnOverlay).setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        val etMaxUses = findViewById<EditText>(R.id.etMaxUses)
        val regionPrefs = getSharedPreferences(RegionPrefs.NAME, MODE_PRIVATE)
        val savedMax = regionPrefs.getInt(RegionPrefs.KEY_MAX_USES, 0)
        if (savedMax > 0) etMaxUses.setText(savedMax.toString())

        findViewById<Button>(R.id.btnSaveMaxUses).setOnClickListener {
            val value = etMaxUses.text.toString().trim().toIntOrNull() ?: 0
            regionPrefs.edit().putInt(RegionPrefs.KEY_MAX_USES, value).apply()
            val msg = if (value > 0) "Đã lưu giới hạn: $value lần thả cần" else "Đã bỏ giới hạn (không giới hạn số lần)"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnPickRegion).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Hãy cấp quyền Overlay trước", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val intent = Intent(this, OverlayService::class.java)
            intent.action = OverlayService.ACTION_SELECT_REGION
            startService(intent)
            moveTaskToBack(true)
        }

        findViewById<Button>(R.id.btnStart).setOnClickListener {
            if (!isAccessibilityServiceEnabled()) {
                Toast.makeText(this, "Hãy bật Accessibility Service trước", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Hãy cấp quyền Overlay trước", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val prefs = getSharedPreferences(RegionPrefs.NAME, MODE_PRIVATE)
            if (!prefs.contains(RegionPrefs.KEY_X)) {
                Toast.makeText(this, "Hãy chọn vùng phao câu trước", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val mgr = getSystemService(MediaProjectionManager::class.java)
            startActivityForResult(mgr.createScreenCaptureIntent(), REQ_MEDIA_PROJECTION)
        }
    }

    override fun onResume() {
        super.onResume()
        tvStep1.text = if (isAccessibilityServiceEnabled())
            "Bước 1: Quyền Accessibility - ĐÃ CẤP ✓"
        else
            "Bước 1: Cấp quyền Accessibility (chưa cấp)"

        tvStep2.text = if (Settings.canDrawOverlays(this))
            "Bước 2: Quyền hiển thị đè - ĐÃ CẤP ✓"
        else
            "Bước 2: Cấp quyền hiển thị đè (chưa cấp)"
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            val serviceIntent = Intent(this, ScreenCaptureService::class.java)
            serviceIntent.putExtra("resultCode", resultCode)
            serviceIntent.putExtra("data", data)
            startForegroundService(serviceIntent)
            Toast.makeText(this, "Đã bắt đầu. Mở Minecraft và dùng bong bóng nổi để Start/Stop.", Toast.LENGTH_LONG).show()
            moveTaskToBack(true)
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val expected = "$packageName/${FishingAccessibilityService::class.java.canonicalName}"
        val enabled = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val splitter = TextUtils.SimpleStringSplitter(':')
        splitter.setString(enabled)
        while (splitter.hasNext()) {
            if (splitter.next().equals(expected, ignoreCase = true)) return true
        }
        return false
    }

    companion object {
        private const val REQ_MEDIA_PROJECTION = 1001
    }
}
