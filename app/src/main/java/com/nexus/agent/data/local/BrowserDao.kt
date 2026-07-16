package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "browser_bookmarks")
data class BookmarkEntity(
    @PrimaryKey val id: String,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val folder: String = "default",
    val createdAt: Long = System.currentTimeMillis(),
    val visitCount: Int = 0,
    val isFavorite: Boolean = false
)

@Entity(tableName = "browser_history")
data class BrowserHistoryEntity(
    @PrimaryKey val id: String,
    val title: String? = null,
    val url: String,
    val visitedAt: Long = System.currentTimeMillis(),
    val visitDurationMs: Long = 0
)

@Entity(tableName = "browser_cookies")
data class CookieEntity(
    @PrimaryKey val id: String,
    val domain: String,
    val name: String,
    val value: String,
    val path: String = "/",
    val expiresAt: Long? = null,
    val isSecure: Boolean = false,
    val isHttpOnly: Boolean = false
)

@Entity(tableName = "browser_tabs")
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String,
    val title: String? = null,
    val position: Int = 0,
    val isActive: Boolean = false,
    val snapshotPath: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface BrowserDao {
    // Bookmarks
    @Query("SELECT * FROM browser_bookmarks ORDER BY createdAt DESC")
    fun getAllBookmarks(): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM browser_bookmarks WHERE folder = :folder ORDER BY createdAt DESC")
    fun getBookmarksByFolder(folder: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM browser_bookmarks WHERE url = :url LIMIT 1")
    suspend fun getBookmarkByUrl(url: String): BookmarkEntity?

    @Query("SELECT * FROM browser_bookmarks WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%'")
    fun searchBookmarks(query: String): Flow<List<BookmarkEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBookmark(bookmark: BookmarkEntity)

    @Update
    suspend fun updateBookmark(bookmark: BookmarkEntity)

    @Query("UPDATE browser_bookmarks SET visitCount = visitCount + 1 WHERE id = :bookmarkId")
    suspend fun incrementVisitCount(bookmarkId: String)

    @Delete
    suspend fun deleteBookmark(bookmark: BookmarkEntity)

    @Query("DELETE FROM browser_bookmarks WHERE id = :bookmarkId")
    suspend fun deleteBookmarkById(bookmarkId: String)

    // History
    @Query("SELECT * FROM browser_history ORDER BY visitedAt DESC")
    fun getAllHistory(): Flow<List<BrowserHistoryEntity>>

    @Query("SELECT * FROM browser_history WHERE visitedAt > :since ORDER BY visitedAt DESC")
    fun getRecentHistory(since: Long): Flow<List<BrowserHistoryEntity>>

    @Query("SELECT * FROM browser_history WHERE url LIKE '%' || :domain || '%' ORDER BY visitedAt DESC")
    fun getHistoryByDomain(domain: String): Flow<List<BrowserHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistoryEntry(entry: BrowserHistoryEntity)

    @Query("DELETE FROM browser_history WHERE visitedAt < :olderThan")
    suspend fun deleteOldHistory(olderThan: Long)

    @Query("DELETE FROM browser_history")
    suspend fun clearHistory()

    // Cookies
    @Query("SELECT * FROM browser_cookies WHERE domain = :domain")
    suspend fun getCookiesByDomain(domain: String): List<CookieEntity>

    @Query("SELECT * FROM browser_cookies WHERE name = :name AND domain = :domain LIMIT 1")
    suspend fun getCookie(name: String, domain: String): CookieEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCookie(cookie: CookieEntity)

    @Query("DELETE FROM browser_cookies WHERE domain = :domain")
    suspend fun deleteCookiesByDomain(domain: String)

    @Query("DELETE FROM browser_cookies WHERE expiresAt < :now")
    suspend fun deleteExpiredCookies(now: Long = System.currentTimeMillis())

    // Tabs
    @Query("SELECT * FROM browser_tabs ORDER BY position ASC")
    fun getAllTabs(): Flow<List<TabEntity>>

    @Query("SELECT * FROM browser_tabs WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveTab(): TabEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTab(tab: TabEntity)

    @Query("UPDATE browser_tabs SET isActive = 0")
    suspend fun clearActiveTabs()

    @Query("UPDATE browser_tabs SET isActive = 1 WHERE id = :tabId")
    suspend fun setActiveTab(tabId: String)

    @Delete
    suspend fun deleteTab(tab: TabEntity)

    @Query("DELETE FROM browser_tabs")
    suspend fun deleteAllTabs()
}
