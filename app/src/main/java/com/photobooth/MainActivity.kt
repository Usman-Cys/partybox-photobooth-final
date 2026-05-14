package com.photobooth

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.photobooth.databinding.ActivityMainBinding
import com.photobooth.hardware.VendorLightController
import com.photobooth.printer.PrintQueueManager
import com.photobooth.printer.PrinterStatus
import com.photobooth.printer.UartPrinterClient
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.launch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var printerManager: PrintQueueManager
    private var imageCapture: ImageCapture? = null
    private val lightController = VendorLightController()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cameraExecutor = Executors.newSingleThreadExecutor()
        printerManager = PrintQueueManager(UartPrinterClient(), lifecycleScope)

        bindClicks()
        observePrinterStatus()
        updateUiForPermissions()
    }

    private fun bindClicks() {
        binding.grantPermissionButton.setOnClickListener {
            ActivityCompat.requestPermissions(this, requiredPermissions().toTypedArray(), REQ_PERMISSIONS)
        }

        binding.captureButton.setOnClickListener {
            capturePhoto()
        }

        binding.retakeButton.setOnClickListener {
            showCameraPanel()
        }

        binding.printButton.setOnClickListener {
            printerManager.enqueue(1)
            binding.statusText.text = "Queued print"
        }

        binding.settingsButton.setOnClickListener {
            showSettingsPanel()
        }

        binding.closeSettingsButton.setOnClickListener {
            showCameraPanel()
        }

        binding.ringSlider.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                lightController.setRingBrightness(value.toInt())
            }
        }

        binding.flashSwitch.setOnCheckedChangeListener { _, isChecked ->
            lightController.setFlashEnabled(isChecked)
        }
    }

    private fun observePrinterStatus() {
        lifecycleScope.launch {
            printerManager.status.collect { status ->
                binding.statusText.text = when (status) {
                    PrinterStatus.Idle -> "Ready"
                    PrinterStatus.Printing -> "Printing..."
                    is PrinterStatus.Busy -> "Printer busy, retrying..."
                    PrinterStatus.Done -> "Print completed"
                    is PrinterStatus.Error -> "Print error: ${status.message}"
                }
            }
        }
    }

    private fun updateUiForPermissions() {
        if (allPermissionsGranted()) {
            startCamera()
            showCameraPanel()
        } else {
            showSetupPanel()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(binding.previewView.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        val outputFile = PrintQueueManager.ensurePrintFile()
        val outputOptions = ImageCapture.OutputFileOptions.Builder(outputFile).build()
        imageCapture.takePicture(
            outputOptions,
            cameraExecutor,
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        binding.statusText.text = "Capture failed: ${exception.message}"
                    }
                }

                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        val bitmap = BitmapFactory.decodeFile(outputFile.absolutePath)
                        binding.capturedImageView.setImageBitmap(bitmap)
                        showPreviewPanel()
                        binding.statusText.text = "Captured. Ready to print"
                    }
                }
            }
        )
    }

    private fun showSetupPanel() {
        binding.setupPanel.visibility = android.view.View.VISIBLE
        binding.cameraPanel.visibility = android.view.View.GONE
        binding.previewPanel.visibility = android.view.View.GONE
        binding.settingsPanel.visibility = android.view.View.GONE
    }

    private fun showCameraPanel() {
        binding.setupPanel.visibility = android.view.View.GONE
        binding.cameraPanel.visibility = android.view.View.VISIBLE
        binding.previewPanel.visibility = android.view.View.GONE
        binding.settingsPanel.visibility = android.view.View.GONE
    }

    private fun showPreviewPanel() {
        binding.setupPanel.visibility = android.view.View.GONE
        binding.cameraPanel.visibility = android.view.View.GONE
        binding.previewPanel.visibility = android.view.View.VISIBLE
        binding.settingsPanel.visibility = android.view.View.GONE
    }

    private fun showSettingsPanel() {
        binding.setupPanel.visibility = android.view.View.GONE
        binding.cameraPanel.visibility = android.view.View.GONE
        binding.previewPanel.visibility = android.view.View.GONE
        binding.settingsPanel.visibility = android.view.View.VISIBLE
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            updateUiForPermissions()
        }
    }

    private fun allPermissionsGranted(): Boolean = requiredPermissions().all {
        ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requiredPermissions(): List<String> {
        val perms = mutableListOf(Manifest.permission.CAMERA)
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            perms += Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
            perms += Manifest.permission.WRITE_EXTERNAL_STORAGE
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            perms += Manifest.permission.READ_MEDIA_IMAGES
        }
        return perms
    }

    override fun onDestroy() {
        runBlocking {
            printerManager.shutdown()
        }
        cameraExecutor.shutdown()
        super.onDestroy()
    }

    companion object {
        private const val REQ_PERMISSIONS = 1010
    }
}
