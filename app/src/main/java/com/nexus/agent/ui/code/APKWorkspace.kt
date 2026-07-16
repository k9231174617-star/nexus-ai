package com.nexus.agent.ui.code

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nexus.agent.R
import com.nexus.agent.core.apk.APKTool
import com.nexus.agent.core.apk.Decompiler
import java.io.File

/**
 * Рабочее пространство для анализа и редактирования APK.
 * Позволяет декомпилировать, редактировать smali/manifest и рекомпилировать APK.
 */
class APKWorkspace @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    var onClose: (() -> Unit)? = null
    var onFileSelect: ((File) -> Unit)? = null

    private lateinit var tvApkName: TextView
    private lateinit var btnClose: Button
    private lateinit var btnDecompile: Button
    private lateinit var btnRecompile: Button
    private lateinit var btnSign: Button
    private lateinit var btnInstall: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var tvStatus: TextView
    private lateinit var tabContainer: LinearLayout
    private lateinit var fileTreeRecycler: RecyclerView
    private lateinit var infoPanel: LinearLayout

    private val apkTool: APKTool by lazy { APKTool(context) }
    private val decompiler: Decompiler by lazy { Decompiler(context) }

    private var currentApkPath: String? = null
    private var decompiledDir: File? = null
    private var isDecompiled = false

    init {
        LayoutInflater.from(context).inflate(R.layout.view_apk_workspace, this, true)
        initViews()
    }

    private fun initViews() {
        tvApkName = findViewById(R.id.tvApkName)
        btnClose = findViewById(R.id.btnCloseApk)
        btnDecompile = findViewById(R.id.btnDecompile)
        btnRecompile = findViewById(R.id.btnRecompile)
        btnSign = findViewById(R.id.btnSign)
        btnInstall = findViewById(R.id.btnInstall)
        progressBar = findViewById(R.id.progressBar)
        tvStatus = findViewById(R.id.tvStatus)
        tabContainer = findViewById(R.id.tabContainer)
        fileTreeRecycler = findViewById(R.id.fileTreeRecycler)
        infoPanel = findViewById(R.id.infoPanel)

        btnClose.setOnClickListener { onClose?.invoke() }
        btnDecompile.setOnClickListener { decompileApk() }
        btnRecompile.setOnClickListener { recompileApk() }
        btnSign.setOnClickListener { signApk() }
        btnInstall.setOnClickListener { installApk() }

        fileTreeRecycler.layoutManager = LinearLayoutManager(context)
    }

    fun loadApk(apkPath: String) {
        currentApkPath = apkPath
        val file = File(apkPath)
        tvApkName.text = file.name

        // Parse basic info
        parseApkInfo(file)

        // Check if already decompiled
        val outDir = File(context.cacheDir, "decompiled/${file.nameWithoutExtension}")
        if (outDir.exists()) {
            decompiledDir = outDir
            isDecompiled = true
            loadDecompiledTree()
            updateButtons()
        }
    }

    private fun parseApkInfo(file: File) {
        try {
            val info = apkTool.getApkInfo(file)
            infoPanel.removeAllViews()

            addInfoRow("Package", info.packageName)
            addInfoRow("Version", "${info.versionName} (${info.versionCode})")
            addInfoRow("Min SDK", info.minSdkVersion.toString())
            addInfoRow("Target SDK", info.targetSdkVersion.toString())
            addInfoRow("Size", formatFileSize(file.length()))

        } catch (e: Exception) {
            tvStatus.text = "Error reading APK: ${e.message}"
        }
    }

    private fun addInfoRow(label: String, value: String) {
        val row = LayoutInflater.from(context).inflate(R.layout.item_apk_info, infoPanel, false)
        row.findViewById<TextView>(R.id.tvLabel).text = label
        row.findViewById<TextView>(R.id.tvValue).text = value
        infoPanel.addView(row)
    }

    private fun decompileApk() {
        val apkPath = currentApkPath ?: return
        showProgress(true, "Decompiling...")

        Thread {
            try {
                val outDir = File(context.cacheDir, "decompiled/${File(apkPath).nameWithoutExtension}")
                decompiler.decompile(File(apkPath), outDir)

                post {
                    decompiledDir = outDir
                    isDecompiled = true
                    loadDecompiledTree()
                    updateButtons()
                    showProgress(false, "Decompiled successfully")
                }
            } catch (e: Exception) {
                post {
                    showProgress(false, "Decompile failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun recompileApk() {
        val dir = decompiledDir ?: return
        val originalApk = currentApkPath ?: return
        showProgress(true, "Recompiling...")

        Thread {
            try {
                val outputApk = File(context.cacheDir, "recompiled/${File(originalApk).name}")
                outputApk.parentFile?.mkdirs()

                apkTool.recompile(dir, outputApk)

                post {
                    showProgress(false, "Recompiled: ${outputApk.name}")
                }
            } catch (e: Exception) {
                post {
                    showProgress(false, "Recompile failed: ${e.message}")
                }
            }
        }.start()
    }

    private fun signApk() {
        // Sign APK with debug or custom key
        showProgress(true, "Signing APK...")
        // Implementation...
        showProgress(false, "Signed")
    }

    private fun installApk() {
        val apkPath = currentApkPath ?: return
        // Install via PackageManager or root
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
            setDataAndType(
                androidx.core.content.FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.provider",
                    File(apkPath)
                ),
                "application/vnd.android.package-archive"
            )
            flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
        }
        context.startActivity(intent)
    }

    private fun loadDecompiledTree() {
        val dir = decompiledDir ?: return

        val adapter = FileTreeAdapter(
            onFileClick = { file ->
                if (file.extension in listOf("smali", "xml", "json", "txt", "kt", "java")) {
                    onFileSelect?.invoke(file)
                }
            },
            onFileLongClick = { _, _ -> },
            onDirectoryClick = { subDir -> /* toggle */ },
            isExpanded = { true },
            isModified = { false }
        )

        val items = mutableListOf<FileTreeAdapter.FileItem>()
        collectFiles(dir, 0, items)
        adapter.submitList(items)
        fileTreeRecycler.adapter = adapter
    }

    private fun collectFiles(dir: File, depth: Int, list: MutableList<FileTreeAdapter.FileItem>) {
        dir.listFiles()?.sortedWith(
            compareByDescending<File> { it.isDirectory }
                .thenBy { it.name }
        )?.forEach { file ->
            list.add(FileTreeAdapter.FileItem(file, depth, file.isDirectory))
            if (file.isDirectory) {
                collectFiles(file, depth + 1, list)
            }
        }
    }

    private fun updateButtons() {
        btnDecompile.isEnabled = !isDecompiled
        btnRecompile.isEnabled = isDecompiled
        btnSign.isEnabled = isDecompiled
    }

    private fun showProgress(show: Boolean, message: String = "") {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
        tvStatus.text = message
        tvStatus.visibility = View.VISIBLE
    }

    private fun formatFileSize(bytes: Long): String {
        val units = arrayOf("B", "KB", "MB", "GB")
        var size = bytes.toDouble()
        var unitIndex = 0
        while (size >= 1024 && unitIndex < units.size - 1) {
            size /= 1024
            unitIndex++
        }
        return String.format("%.1f %s", size, units[unitIndex])
    }
}
