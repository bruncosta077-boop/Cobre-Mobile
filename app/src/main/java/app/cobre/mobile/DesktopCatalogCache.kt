package app.cobre.mobile

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest

/** Private, metadata-only snapshot used to keep the paired Desktop library visible during brief outages. */
class DesktopCatalogCache(context:Context){
    private val db=CacheDatabase(context.applicationContext)

    suspend fun read(cacheKey:String):List<Track> = withContext(Dispatchers.IO){
        val rows=db.readableDatabase.query("desktop_tracks",arrayOf("origin","track_id","title","artist","album","track_number","duration_ms","year"),"cache_key=?",arrayOf(cacheKey),null,null,"artist COLLATE NOCASE, album COLLATE NOCASE, track_number")
        rows.use{cursor->buildList(cursor.count){while(cursor.moveToNext()){
            val origin=cursor.getString(0);val id=cursor.getInt(1)
            val stream=Uri.parse(origin).buildUpon().appendPath("v1").appendPath("tracks").appendPath(id.toString()).appendPath("stream").build()
            val artist=cursor.getString(3)
            add(Track(-id.toLong()-1,stream,cursor.getString(2),artist,artist,cursor.getString(4),cursor.getInt(5),0,cursor.getLong(6),cursor.getInt(7)))
        }}}
    }

    suspend fun replace(cacheKey:String,origin:String,tracks:List<Track>)=withContext(Dispatchers.IO){
        val database=db.writableDatabase
        database.beginTransaction()
        try{
            database.delete("desktop_tracks","cache_key=?",arrayOf(cacheKey))
            tracks.forEach{track->
                val id=Regex("/v1/tracks/(\\d+)/stream").matchEntire(track.uri.path.orEmpty())?.groupValues?.getOrNull(1)?.toIntOrNull()?:return@forEach
                val values=ContentValues().apply{put("cache_key",cacheKey);put("origin",origin);put("track_id",id);put("title",track.title);put("artist",track.artist);put("album",track.album);put("track_number",track.trackNumber);put("duration_ms",track.durationMs);put("year",track.year)}
                database.insertWithOnConflict("desktop_tracks",null,values,SQLiteDatabase.CONFLICT_REPLACE)
            }
            database.setTransactionSuccessful()
        }finally{database.endTransaction()}
    }

    suspend fun clear(cacheKey:String)=withContext(Dispatchers.IO){db.writableDatabase.delete("desktop_tracks","cache_key=?",arrayOf(cacheKey))}

    companion object{
        fun key(origin:String,grantId:String):String=MessageDigest.getInstance("SHA-256").digest("$origin|$grantId".toByteArray()).joinToString(""){"%02x".format(it)}
    }

    private class CacheDatabase(context:Context):SQLiteOpenHelper(context,"cobre-desktop-catalog-cache.db",null,1){
        override fun onCreate(db:SQLiteDatabase){db.execSQL("CREATE TABLE desktop_tracks (cache_key TEXT NOT NULL, origin TEXT NOT NULL, track_id INTEGER NOT NULL, title TEXT NOT NULL, artist TEXT NOT NULL, album TEXT NOT NULL, track_number INTEGER NOT NULL, duration_ms INTEGER NOT NULL, year INTEGER NOT NULL, PRIMARY KEY(cache_key,track_id))")}
        override fun onUpgrade(db:SQLiteDatabase,oldVersion:Int,newVersion:Int)=Unit
    }
}
