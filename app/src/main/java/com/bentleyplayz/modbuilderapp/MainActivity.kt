package com.bentleyplayz.modbuilderapp

import android.app.Activity
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.text.InputType
import android.util.Base64
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class MainActivity : Activity() {

    private lateinit var tokenEt: EditText
    private lateinit var repoEt: EditText
    private lateinit var branchEt: EditText
    private lateinit var logTv: TextView
    private lateinit var logScroll: ScrollView

    @Volatile private var busy = false
    @Volatile private var tok = ""
    @Volatile private var repo = ""
    @Volatile private var branch = "main"

    companion object {
        const val REQ_SRC = 101
        const val REQ_DLL = 102
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("cfg", MODE_PRIVATE)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 64, 32, 32)
        }
        fun label(t: String) = TextView(this).apply { text = t; textSize = 13f; setPadding(0, 16, 0, 0) }

        root.addView(TextView(this).apply { text = "Mod Builder"; textSize = 24f })
        root.addView(label("GitHub token (needs Contents + Actions access)"))
        tokenEt = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(prefs.getString("token", ""))
        }
        root.addView(tokenEt)
        root.addView(label("Repo that has the workflows (owner/name)"))
        repoEt = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString("repo", "bentleyplayz59-creator/mod-builder"))
        }
        root.addView(repoEt)
        root.addView(label("Branch"))
        branchEt = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_TEXT
            setText(prefs.getString("branch", "main"))
        }
        root.addView(branchEt)

        val btnBuild = Button(this).apply { text = "Pick source ZIP and build mod" }
        val btnDec = Button(this).apply { text = "Pick DLL and decompile" }
        btnBuild.setOnClickListener { pick(REQ_SRC) }
        btnDec.setOnClickListener { pick(REQ_DLL) }
        root.addView(btnBuild)
        root.addView(btnDec)

        logTv = TextView(this).apply { textSize = 13f; setPadding(0, 24, 0, 0) }
        logScroll = ScrollView(this).apply { addView(logTv) }
        root.addView(logScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)

        log("Source ZIP must contain your .csproj, manifest.json and assets.")
    }

    private fun pick(req: Int) {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        startActivityForResult(i, req)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode == RESULT_OK && uri != null) {
            if (requestCode == REQ_SRC) startJob(uri, true)
            if (requestCode == REQ_DLL) startJob(uri, false)
        }
    }

    private fun log(msg: String) = runOnUiThread {
        logTv.append(msg + "\n")
        logScroll.post { logScroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun displayName(uri: Uri): String {
        contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0 && c.moveToFirst()) return c.getString(idx)
        }
        return "file"
    }

    private fun startJob(uri: Uri, isBuild: Boolean) {
        if (busy) { log("A job is already running."); return }
        tok = tokenEt.text.toString().trim()
        repo = repoEt.text.toString().trim()
        branch = branchEt.text.toString().trim().ifEmpty { "main" }
        if (tok.isEmpty() || !repo.contains("/")) { log("Fill in the token and owner/name first."); return }
        getSharedPreferences("cfg", MODE_PRIVATE).edit()
            .putString("token", tok).putString("repo", repo).putString("branch", branch).apply()

        val name = displayName(uri)
        val ext = if (isBuild) ".zip" else ".dll"
        if (!name.lowercase().endsWith(ext)) { log("Pick a $ext file."); return }

        busy = true
        Thread {
            try {
                val bytes = contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                val safe = name.replace(Regex("[^A-Za-z0-9._-]"), "_")
                val dir = if (isBuild) "inbox" else "decompile"
                val path = "$dir/${System.currentTimeMillis() / 1000}_$safe"
                log("Uploading $name (${bytes.size / 1024} KB)...")
                val sha = upload(path, bytes)
                log("Uploaded. Waiting for the workflow to start...")
                val wfName = if (isBuild) "Build Mod" else "Decompile DLL"
                val run = waitForRun(sha, wfName)
                val runId = run.getLong("id")
                val conclusion = run.optString("conclusion")
                if (conclusion != "success") {
                    log("Workflow finished: $conclusion")
                    logFailedStep(runId)
                    log(run.optString("html_url"))
                    return@Thread
                }
                log("Workflow succeeded. Downloading result...")
                downloadArtifacts(runId)
            } catch (e: Exception) {
                log("Error: ${e.message}")
            } finally {
                busy = false
            }
        }.start()
    }

    private fun api(method: String, path: String, body: ByteArray? = null): Pair<Int, String> {
        val c = URL("https://api.github.com$path").openConnection() as HttpURLConnection
        c.requestMethod = method
        c.setRequestProperty("Authorization", "Bearer $tok")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
        c.connectTimeout = 30000
        c.readTimeout = 120000
        if (body != null) {
            c.doOutput = true
            c.setRequestProperty("Content-Type", "application/json")
            c.setFixedLengthStreamingMode(body.size)
            c.outputStream.use { it.write(body) }
        }
        val code = c.responseCode
        val stream = if (code < 400) c.inputStream else c.errorStream
        val text = stream?.bufferedReader()?.use { it.readText() } ?: ""
        return code to text
    }

    private fun upload(path: String, bytes: ByteArray): String {
        val json = JSONObject()
            .put("message", "Mod Builder upload: $path")
            .put("content", Base64.encodeToString(bytes, Base64.NO_WRAP))
            .put("branch", branch)
        val (code, text) = api("PUT", "/repos/$repo/contents/$path", json.toString().toByteArray())
        if (code != 200 && code != 201) throw RuntimeException("Upload failed ($code): ${text.take(300)}")
        return JSONObject(text).getJSONObject("commit").getString("sha")
    }

    private fun waitForRun(sha: String, wfName: String): JSONObject {
        val deadline = System.currentTimeMillis() + 25 * 60 * 1000
        var lastStatus = ""
        while (System.currentTimeMillis() < deadline) {
            val (code, text) = api("GET", "/repos/$repo/actions/runs?head_sha=$sha&per_page=20")
            if (code == 200) {
                val arr = JSONObject(text).getJSONArray("workflow_runs")
                for (i in 0 until arr.length()) {
                    val r = arr.getJSONObject(i)
                    if (r.optString("name") != wfName) continue
                    val status = r.optString("status")
                    if (status != lastStatus) { log("Status: $status"); lastStatus = status }
                    if (status == "completed") return r
                }
            }
            Thread.sleep(5000)
        }
        throw RuntimeException("Timed out waiting for the workflow. Check the Actions tab.")
    }

    private fun logFailedStep(runId: Long) {
        val (code, text) = api("GET", "/repos/$repo/actions/runs/$runId/jobs")
        if (code != 200) return
        val jobs = JSONObject(text).getJSONArray("jobs")
        for (i in 0 until jobs.length()) {
            val steps = jobs.getJSONObject(i).optJSONArray("steps") ?: continue
            for (j in 0 until steps.length()) {
                val s = steps.getJSONObject(j)
                if (s.optString("conclusion") == "failure") log("Failed step: ${s.optString("name")}")
            }
        }
    }

    private fun downloadArtifacts(runId: Long) {
        val (code, text) = api("GET", "/repos/$repo/actions/runs/$runId/artifacts")
        if (code != 200) throw RuntimeException("Could not list artifacts ($code)")
        val arts = JSONObject(text).getJSONArray("artifacts")
        if (arts.length() == 0) throw RuntimeException("The run produced no artifact.")
        val id = arts.getJSONObject(0).getLong("id")

        // GitHub redirects to blob storage; do not forward the token there.
        val c = URL("https://api.github.com/repos/$repo/actions/artifacts/$id/zip").openConnection() as HttpURLConnection
        c.instanceFollowRedirects = false
        c.setRequestProperty("Authorization", "Bearer $tok")
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val loc = if (c.responseCode in 300..399) c.getHeaderField("Location") else null
        val stream: InputStream = if (loc != null) URL(loc).openStream()
        else if (c.responseCode == 200) c.inputStream
        else throw RuntimeException("Artifact download failed (${c.responseCode})")

        var saved = 0
        ZipInputStream(stream).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                if (e.isDirectory) continue
                val fileName = File(e.name).name
                saveToDownloads(fileName, zis)
                log("Saved to Downloads: $fileName")
                saved++
            }
        }
        if (saved == 0) log("Artifact was empty.")
    }

    private fun saveToDownloads(name: String, data: InputStream) {
        val mime = if (name.endsWith(".zip")) "application/zip" else "application/octet-stream"
        val v = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, name)
            put(MediaStore.Downloads.MIME_TYPE, mime)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)
            ?: throw RuntimeException("Could not create file in Downloads")
        contentResolver.openOutputStream(uri)!!.use { data.copyTo(it) }
    }
}
