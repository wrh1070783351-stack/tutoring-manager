package com.wrh.keshiguanjia.data

import android.content.Context
import android.content.Intent
import java.io.File
import java.io.InputStream
import java.time.LocalDate

/** 整库备份 / 恢复（SQLite 文件级）。 */
object BackupManager {

    private const val SQLITE_HEADER = "SQLite format 3"

    /** 备份：合并 WAL → 拷贝主库文件到缓存并返回（随后走分享面板存到任意位置）。 */
    fun backup(context: Context, db: KeshiDatabase): File? {
        // 合并 wal 日志到主文件，保证拷贝完整
        runCatching {
            db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { it.moveToFirst() }
        }
        val dbFile = context.getDatabasePath(KeshiDatabase.NAME)
        if (!dbFile.exists()) return null
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val out = File(dir, "keshi-backup-${LocalDate.now()}.db")
        dbFile.copyTo(out, overwrite = true)
        return out
    }

    /** 校验文件是否为合法 SQLite 库（读文件头）。 */
    fun looksLikeSqlite(file: File): Boolean {
        if (!file.exists() || file.length() < 16) return false
        val header = ByteArray(15)
        file.inputStream().use { it.read(header) }
        return String(header, Charsets.US_ASCII) == SQLITE_HEADER
    }

    /**
     * 恢复：从输入流落盘 → 校验 SQLite 头 → 关闭当前库 → 替换主库文件。
     * 成功后调用方需提示用户并 [restartApp]。
     */
    fun restore(context: Context, input: InputStream, db: KeshiDatabase): Boolean {
        val tmp = File(context.cacheDir, "restore-tmp.db")
        runCatching {
            input.use { ins -> tmp.outputStream().use { ins.copyTo(it) } }
        }.onFailure { return false }
        if (!looksLikeSqlite(tmp)) {
            tmp.delete()
            return false
        }
        return runCatching {
            db.close()
            val dbFile = context.getDatabasePath(KeshiDatabase.NAME)
            File(dbFile.path + "-wal").delete()
            File(dbFile.path + "-shm").delete()
            tmp.copyTo(dbFile, overwrite = true)
            tmp.delete()
            true
        }.getOrDefault(false)
    }

    /** 重启应用（恢复完成后让所有单例/连接重建）。 */
    fun restartApp(context: Context) {
        val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        intent?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        context.startActivity(intent)
        Runtime.getRuntime().exit(0)
    }
}
