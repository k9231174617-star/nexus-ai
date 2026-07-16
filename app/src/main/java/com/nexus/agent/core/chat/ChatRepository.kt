package com.nexus.agent.core.chat

import android.content.Context
import com.nexus.agent.data.local.AppDatabase

class ChatRepository @JvmOverloads constructor(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context)
) {
    private val chatDao = database.chatDao()

    suspend fun getAllChats() = chatDao.getAllChats()
    
    suspend fun getChatById(id: Long) = chatDao.getChatById(id)
    
    suspend fun insertChat(chat: ChatModel) = chatDao.insertChat(chat)
    
    suspend fun updateChat(chat: ChatModel) = chatDao.updateChat(chat)
    
    suspend fun deleteChat(chat: ChatModel) = chatDao.deleteChat(chat)
    
    suspend fun insertMessage(message: MessageModel) = chatDao.insertMessage(message)
    
    suspend fun getMessagesForChat(chatId: Long) = chatDao.getMessagesForChat(chatId)
    
    suspend fun deleteMessagesForChat(chatId: Long) = chatDao.deleteMessagesForChat(chatId)
}
