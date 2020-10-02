package com.umarabbas.musicplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.recyclerview.widget.RecyclerView

class RVAdapter(context : Context, var playSong: (Song) -> Unit) : RecyclerView.Adapter<RVAdapter.ViewHolder>() {
    var listOfSongs = mutableListOf<Song>()


    inner class ViewHolder(itemView : View) : RecyclerView.ViewHolder(itemView){
        private val title: TextView = itemView.findViewById(R.id.textViewSongTitle)
        private val artist: TextView = itemView.findViewById(R.id.textViewArtistName)
        val mainItem: ConstraintLayout = itemView.findViewById(R.id.mainConstraint)
        private var position: Int? = null

        fun bind(song: Song, pos: Int) {
            title.text = song.title
            artist.text = song.path
            position = pos
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.track_item, parent, false))
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val song = listOfSongs[position]
        holder.bind(song, position)
        holder.mainItem.setOnClickListener{
            playSong(song)
        }
    }

    override fun getItemCount(): Int {
        return listOfSongs.size
    }

    fun setSongs(songs : List<Song>){
        listOfSongs.addAll(songs)
        notifyDataSetChanged()
    }
}