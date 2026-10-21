package air.svran.app.xp.strings

import android.content.res.Resources
import android.os.ParcelFileDescriptor
import android.util.Log
import io.github.libxposed.api.XposedInterface
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.FileInputStream

class MainModule : XposedModule() {

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        super.onPackageLoaded(param)
        
        val pkgName = param.packageName
        val fileName = "${pkgName}_strings.xml"
        
        // Check for our remote file
        val remoteFiles = listRemoteFiles()
        if (!remoteFiles.contains(fileName)) {
            return
        }

        try {
            val pfd = openRemoteFile(fileName)
            val replacements = parseStringsXml(pfd)
            pfd.close()

            if (replacements.isNotEmpty()) {
                hookResources(param, replacements)
            }
        } catch (e: Exception) {
            Log.e("StringsModule", "Failed to load replacements for $pkgName", e)
        }
    }

    private fun parseStringsXml(pfd: ParcelFileDescriptor): Map<String, String> {
        val map = mutableMapOf<String, String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(FileInputStream(pfd.fileDescriptor), "UTF-8")

            var eventType = parser.eventType
            var currentName: String? = null

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (parser.name == "string") {
                            currentName = parser.getAttributeValue(null, "name")
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (currentName != null) {
                            map[currentName] = parser.text
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "string") {
                            currentName = null
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e("StringsModule", "Error parsing xml", e)
        }
        return map
    }

    private fun hookResources(param: XposedModuleInterface.PackageLoadedParam, replacements: Map<String, String>) {
        val idToReplacement = mutableMapOf<Int, String>()
        var isCacheInitialized = false

        val hooker = object : XposedInterface.Hooker {
            override fun intercept(chain: XposedInterface.Chain): Any? {
                val res = chain.thisObject as? Resources
                if (res != null) {
                    if (!isCacheInitialized) {
                        replacements.forEach { (name, value) ->
                            val id = res.getIdentifier(name, "string", param.packageName)
                            if (id != 0) {
                                idToReplacement[id] = value
                            }
                        }
                        isCacheInitialized = true
                    }

                    val args = chain.args
                    if (args.isNotEmpty()) {
                        val id = args[0] as? Int
                        if (id != null && idToReplacement.containsKey(id)) {
                            return idToReplacement[id]
                        }
                    }
                }
                return chain.proceed()
            }
        }

        try {
            val getTextMethod1 = Resources::class.java.getDeclaredMethod("getText", Int::class.java)
            hook(getTextMethod1).intercept(hooker)
            
            val getTextMethod2 = Resources::class.java.getDeclaredMethod("getText", Int::class.java, CharSequence::class.java)
            hook(getTextMethod2).intercept(hooker)
        } catch (e: Exception) {
            Log.e("StringsModule", "Failed to hook Resources", e)
        }
    }
}
