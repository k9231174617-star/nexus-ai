package com.nexus.agent.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val name: String,
    val path: String,
    val type: String, // android, kotlin, java, etc.
    val createdAt: Long = System.currentTimeMillis(),
    val lastOpenedAt: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    val description: String? = null,
    val packageName: String? = null,
    val gradleVersion: String? = null,
    val targetSdk: Int? = null,
    val fileCount: Int = 0,
    val totalLines: Int = 0
)

@Dao
interface ProjectDao {
    @Query("SELECT * FROM projects ORDER BY lastOpenedAt DESC")
    fun getAllProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE isFavorite = 1 ORDER BY lastOpenedAt DESC")
    fun getFavoriteProjects(): Flow<List<ProjectEntity>>

    @Query("SELECT * FROM projects WHERE id = :projectId")
    suspend fun getProjectById(projectId: String): ProjectEntity?

    @Query("SELECT * FROM projects WHERE name LIKE '%' || :query || '%' OR path LIKE '%' || :query || '%'")
    fun searchProjects(query: String): Flow<List<ProjectEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProject(project: ProjectEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProjects(projects: List<ProjectEntity>)

    @Update
    suspend fun updateProject(project: ProjectEntity)

    @Query("UPDATE projects SET lastOpenedAt = :timestamp WHERE id = :projectId")
    suspend fun updateLastOpened(projectId: String, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE projects SET isFavorite = :isFavorite WHERE id = :projectId")
    suspend fun setFavorite(projectId: String, isFavorite: Boolean)

    @Delete
    suspend fun deleteProject(project: ProjectEntity)

    @Query("DELETE FROM projects WHERE id = :projectId")
    suspend fun deleteProjectById(projectId: String)

    @Query("DELETE FROM projects")
    suspend fun deleteAllProjects()

    @Query("SELECT COUNT(*) FROM projects")
    fun getProjectCount(): Flow<Int>
}
