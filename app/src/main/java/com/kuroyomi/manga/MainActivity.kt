package com.kuroyomi.manga

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Manga(
    val id: String,
    val title: String,
    val coverUrl: String,
    val tag: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private val mangaList = mutableListOf<Manga>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerManga)
        swipeRefresh = findViewById(R.id.swipeRefresh)
        recyclerView.layoutManager = GridLayoutManager(this, 2)

        findViewById<Button>(R.id.btnAll).setOnClickListener { loadFeed("all") }
        findViewById<Button>(R.id.btnManhwa).setOnClickListener { loadFeed("manhwa") }
        findViewById<Button>(R.id.btnManga).setOnClickListener { loadFeed("manga") }
        findViewById<Button>(R.id.btnR18).setOnClickListener { loadFeed("r18") }

        swipeRefresh.setOnRefreshListener { loadFeed("all") }

        loadFeed("all")
    }

    private fun loadFeed(type: String) {
        swipeRefresh.isRefreshing = true

        val endpoint = when (type) {
            "manhwa" -> "https://api.mangadex.org/manga?limit=26&includes[]=cover_art&originalLanguage[]=ko&contentRating[]=safe&contentRating[]=suggestive"
            "manga" -> "https://api.mangadex.org/manga?limit=26&includes[]=cover_art&originalLanguage[]=ja&contentRating[]=safe&contentRating[]=suggestive"
            "r18" -> "https://api.mangadex.org/manga?limit=26&includes[]=cover_art&contentRating[]=erotica&contentRating[]=pornographic"
            else -> "https://api.mangadex.org/manga?limit=26&includes[]=cover_art&contentRating[]=safe&contentRating[]=suggestive&contentRating[]=erotica"
        }

        Thread {
            try {
                val conn = URL(endpoint).openConnection() as HttpURLConnection
                conn.connectTimeout = 8000
                val text = BufferedReader(InputStreamReader(conn.inputStream)).readText()
                val data = JSONObject(text).getJSONArray("data")

                mangaList.clear()
                for (i in 0 until data.length()) {
                    val item = data.getJSONObject(i)
                    val id = item.getString("id")
                    val attrs = item.getJSONObject("attributes")
                    val titleObj = attrs.getJSONObject("title")
                    val title = titleObj.optString("en", titleObj.optString("ja-ro", "Untitled"))
                    val rating = attrs.optString("contentRating", "safe").uppercase()

                    var cover = ""
                    val rels = item.getJSONArray("relationships")
                    for (j in 0 until rels.length()) {
                        val rel = rels.getJSONObject(j)
                        if (rel.getString("type") == "cover_art") {
                            val f = rel.optJSONObject("attributes")?.optString("fileName", "") ?: ""
                            if (f.isNotEmpty()) cover = "https://uploads.mangadex.org/covers/$id/$f.256.jpg"
                        }
                    }

                    mangaList.add(Manga(id, title, cover, rating))
                }

                runOnUiThread {
                    swipeRefresh.isRefreshing = false
                    recyclerView.adapter = MangaAdapter(mangaList) { selectedManga ->
                        val intent = Intent(this@MainActivity, ReaderActivity::class.java)
                        intent.putExtra("MANGA_ID", selectedManga.id)
                        intent.putExtra("MANGA_TITLE", selectedManga.title)
                        startActivity(intent)
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { swipeRefresh.isRefreshing = false }
            }
        }.start()
    }
}

class MangaAdapter(
    private val list: List<Manga>,
    private val onClick: (Manga) -> Unit
) : RecyclerView.Adapter<MangaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val img: ImageView = view.findViewById(R.id.imgCover)
        val title: TextView = view.findViewById(R.id.txtTitle)
        val tag: TextView = view.findViewById(R.id.badgeTag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_manga, parent, false)
        return ViewHolder(v)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]
        holder.title.text = item.title
        holder.tag.text = item.tag
        holder.img.setImageBitmap(null)

        if (item.coverUrl.isNotEmpty()) {
            Thread {
                try {
                    val bmp = BitmapFactory.decodeStream(URL(item.coverUrl).openStream())
                    holder.img.post { holder.img.setImageBitmap(bmp) }
                } catch (e: Exception) {}
            }.start()
        }

        holder.itemView.setOnClickListener { onClick(item) }
    }

    override fun getItemCount(): Int = list.size
}
