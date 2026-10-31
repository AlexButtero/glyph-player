// ... (остальные импорты)
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    // ... (остальной код)
    
    fun createPlaylist(name: String, onDone: (Long) -> Unit = {}) { 
        scope.launch { val id = repo.createPlaylist(name); withContext(Dispatchers.Main) { onDone(id) } } 
    }
    
    fun updatePlaylist(id: Long, name: String, desc: String?, poster: String?) {
        scope.launch { repo.updatePlaylist(id, name, desc, poster) }
    }
    
    // ... (остальной код)
}
