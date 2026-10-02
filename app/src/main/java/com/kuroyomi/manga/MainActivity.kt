package com.kuroyomi.manga

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

data class Manga(
    val id: String,
    val title: String,
    val coverUrl: String
)

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var progressBar: ProgressBar
    private val mangaList = mutableListOf<Manga>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewManga)
        progressBar = findViewById(R.id.progressBar)

        recyclerView.layoutManager = GridLayoutManager(this, 2)

        fetchMangaList()
    }

    private fun fetchMangaList() {
        Thread {
            try {
                val apiUrl = "https://api.mangadex.org/manga?limit=30&includes[]=cover_art&contentRating[]=safe"
                val url = URL(apiUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val reader = BufferedReader(InputStreamReader(conn.inputStream))
                val response = reader.readText()
                reader.close()

                val json = JSONObject(response)
                val dataArray = json.getJSONArray("data")

                mangaList.clear()

                for (i in 0 until dataArray.length()) {
                    val item = dataArray.getJSONObject(i)
                    val id = item.getString("id")

                    val attributes = item.getJSONObject("attributes")
                    val titleObj = attributes.getJSONObject("title")
                    val title = titleObj.optString("en", titleObj.optString("ja-ro", "Untitled"))

                    var fileName = ""
                    val relationships = item.getJSONArray("relationships")
                    for (j in 0 until relationships.length()) {
                        val rel = relationships.getJSONObject(j)
                        if (rel.getString("type") == "cover_art") {
                            fileName = rel.optJSONObject("attributes")?.optString("fileName", "") ?: ""
                        }
                    }

                    val coverUrl = if (fileName.isNotEmpty()) {
                        "https://uploads.mangadex.org/covers/$id/$fileName.256.jpg"
                    } else {
                        ""
                    }

                    mangaList.add(Manga(id, title, coverUrl))
                }

                runOnUiThread {
                    progressBar.visibility = View.GONE
                    recyclerView.adapter = MangaAdapter(mangaList)
                }

            } catch (e: Exception) {
                e.printStackTrace()
                runOnUiThread {
                    progressBar.visibility = View.GONE
                }
            }
        }.start()
    }
}

class MangaAdapter(private val list: List<Manga>) : RecyclerView.Adapter<MangaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgCover: ImageView = view.findViewById(R.id.imgCover)
        val txtTitle: TextView = view.findViewById(R.id.txtTitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_manga, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val manga = list[position]
        holder.txtTitle.text = manga.title
        holder.imgCover.setImageBitmap(null)

        if (manga.coverUrl.isNotEmpty()) {
            Thread {
                try {
                    val input = URL(manga.coverUrl).openStream()
                    val bitmap: Bitmap = BitmapFactory.decodeStream(input)
                    holder.imgCover.post {
                        holder.imgCover.setImageBitmap(bitmap)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }.start()
        }
    }

    override fun getItemCount(): Int = list.size
}
