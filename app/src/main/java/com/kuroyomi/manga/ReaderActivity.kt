package com.kuroyomi.manga

import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class ReaderActivity : AppCompatActivity() {

    private lateinit var recyclerPages: RecyclerView
    private lateinit var progressBar: ProgressBar
    private val pageUrls = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_reader)

        val mangaId = intent.getStringExtra("MANGA_ID") ?: ""
        val mangaTitle = intent.getStringExtra("MANGA_TITLE") ?: "Reader"

        findViewById<TextView>(R.id.readerTitle).text = mangaTitle
        findViewById<TextView>(R.id.btnBack).setOnClickListener { finish() }

        recyclerPages = findViewById(R.id.recyclerReaderPages)
        progressBar = findViewById(R.id.readerProgress)
        recyclerPages.layoutManager = LinearLayoutManager(this)

        loadChapterPages(mangaId)
    }

    private fun loadChapterPages(mangaId: String) {
        Thread {
            try {
                // 1. Kunin ang pinakabagong English chapter ID
                val feedUrl = "https://api.mangadex.org/manga/$mangaId/feed?translatedLanguage[]=en&order[chapter]=desc&limit=1"
                val conn = URL(feedUrl).openConnection() as HttpURLConnection
                val feedJson = JSONObject(BufferedReader(InputStreamReader(conn.inputStream)).readText())
                val data = feedJson.getJSONArray("data")

                if (data.length() > 0) {
                    val chapterId = data.getJSONObject(0).getString("id")

                    // 2. Kunin ang Server at Image hash para sa mga pahina
                    val atHomeUrl = "https://api.mangadex.org/at-home/server/$chapterId"
                    val homeConn = URL(atHomeUrl).openConnection() as HttpURLConnection
                    val homeJson = JSONObject(BufferedReader(InputStreamReader(homeConn.inputStream)).readText())

                    val baseUrl = homeJson.getString("baseUrl")
                    val chapterObj = homeJson.getJSONObject("chapter")
                    val hash = chapterObj.getString("hash")
                    val files = chapterObj.getJSONArray("data")

                    pageUrls.clear()
                    for (i in 0 until files.length()) {
                        val fileName = files.getString(i)
                        pageUrls.add("$baseUrl/data/$hash/$fileName")
                    }

                    runOnUiThread {
                        progressBar.visibility = View.GONE
                        recyclerPages.adapter = PagesAdapter(pageUrls)
                    }
                } else {
                    runOnUiThread { progressBar.visibility = View.GONE }
                }
            } catch (e: Exception) {
                runOnUiThread { progressBar.visibility = View.GONE }
            }
        }.start()
    }
}

class PagesAdapter(private val pages: List<String>) : RecyclerView.Adapter<PagesAdapter.ViewHolder>() {

    class ViewHolder(val img: ImageView) : RecyclerView.ViewHolder(img)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_reader_page, parent, false) as ImageView
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.img.setImageBitmap(null)
        val url = pages[position]
        Thread {
            try {
                val bmp = BitmapFactory.decodeStream(URL(url).openStream())
                holder.img.post { holder.img.setImageBitmap(bmp) }
            } catch (e: Exception) {}
        }.start()
    }

    override fun getItemCount(): Int = pages.size
}
