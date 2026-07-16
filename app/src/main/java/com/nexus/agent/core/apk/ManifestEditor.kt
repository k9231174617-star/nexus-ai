package com.nexus.agent.core.apk

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.File
import java.io.StringWriter
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

@Singleton
class ManifestEditor @Inject constructor() {

    suspend fun readManifest(manifestPath: String): Map<String, Any> =
        withContext(Dispatchers.IO) {
            val doc = parseXML(manifestPath)
            val root = doc.documentElement
            buildMap {
                put("package", root.getAttribute("package"))
                put("versionName", root.getAttribute("android:versionName"))
                put("versionCode", root.getAttribute("android:versionCode"))
                val appEl = root.getElementsByTagName("application").item(0) as? Element
                put("label", appEl?.getAttribute("android:label") ?: "")
                put("debuggable", appEl?.getAttribute("android:debuggable") ?: "false")
                put("allowBackup", appEl?.getAttribute("android:allowBackup") ?: "true")
                val permissions = mutableListOf<String>()
                val permNodes = root.getElementsByTagName("uses-permission")
                for (i in 0 until permNodes.length) {
                    (permNodes.item(i) as? Element)?.getAttribute("android:name")
                        ?.let { permissions.add(it) }
                }
                put("permissions", permissions)
            }
        }

    suspend fun setDebuggable(manifestPath: String, debuggable: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = parseXML(manifestPath)
                val appEl = doc.documentElement
                    .getElementsByTagName("application").item(0) as? Element
                appEl?.setAttribute("android:debuggable", debuggable.toString())
                saveXML(doc, manifestPath)
                true
            }.getOrDefault(false)
        }

    suspend fun addPermission(manifestPath: String, permission: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = parseXML(manifestPath)
                val root = doc.documentElement
                val el = doc.createElement("uses-permission").apply {
                    setAttribute("android:name", permission)
                }
                root.appendChild(el)
                saveXML(doc, manifestPath)
                true
            }.getOrDefault(false)
        }

    suspend fun removePermission(manifestPath: String, permission: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = parseXML(manifestPath)
                val nodes = doc.documentElement.getElementsByTagName("uses-permission")
                for (i in nodes.length - 1 downTo 0) {
                    val el = nodes.item(i) as? Element
                    if (el?.getAttribute("android:name") == permission) {
                        el.parentNode.removeChild(el)
                    }
                }
                saveXML(doc, manifestPath)
                true
            }.getOrDefault(false)
        }

    suspend fun setPackageName(manifestPath: String, newPackage: String): Boolean =
        withContext(Dispatchers.IO) {
            runCatching {
                val doc = parseXML(manifestPath)
                doc.documentElement.setAttribute("package", newPackage)
                saveXML(doc, manifestPath)
                true
            }.getOrDefault(false)
        }

    private fun parseXML(path: String): Document {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        return builder.parse(File(path))
    }

    private fun saveXML(doc: Document, path: String) {
        val transformer = TransformerFactory.newInstance().newTransformer()
        val writer = StringWriter()
        transformer.transform(DOMSource(doc), StreamResult(writer))
        File(path).writeText(writer.toString())
    }
}