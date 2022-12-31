package com.yomi.yomi

import android.content.Context
import android.database.SQLException
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream


class DataBaseHelperClass
/**
 * Constructor
 * Takes and keeps a reference of the passed context in order to access to the application assets and resources.
 * @param context
 * Parameters of super() are    1. Context
 * 2. Data Base Name.
 * 3. Cursor Factory.
 * 4. Data Base Version.
 */(var context: Context) :
    SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
    /**
     * Creates a empty database on the system and rewrites it with your own database.
     * By calling this method and empty database will be created into the default system path
     * of your application so we are gonna be able to overwrite that database with our database.
     */
    @Throws(IOException::class)
    fun createDataBase() {
        //check if the database exists
        val databaseExist = checkDataBase()
        if (databaseExist) {
            // Do Nothing.
        } else {
            this.writableDatabase
            copyDataBase()
        } // end if else dbExist
    } // end createDataBase().

    /**
     * Check if the database already exist to avoid re-copying the file each time you open the application.
     * @return true if it exists, false if it doesn't
     */
    fun checkDataBase(): Boolean {
        val databaseFile = File(DB_PATH + DATABASE_NAME)
        return databaseFile.exists()
    }

    /**
     * Copies your database from your local assets-folder to the just created empty database in the
     * system folder, from where it can be accessed and handled.
     * This is done by transferring byte stream.
     */
    @Throws(IOException::class)
    private fun copyDataBase() {
        //Open your local db as the input stream
        val myInput = context.assets.open(DATABASE_NAME)
        // Path to the just created empty db
        val outFileName = DB_PATH + DATABASE_NAME
        //Open the empty db as the output stream
        val myOutput: OutputStream = FileOutputStream(outFileName)
        //transfer bytes from the input file to the output file
        val buffer = ByteArray(1024)
        var length: Int
        while (myInput.read(buffer).also { length = it } > 0) {
            myOutput.write(buffer, 0, length)
        }

        //Close the streams
        myOutput.flush()
        myOutput.close()
        myInput.close()
    }

    /**
     * This method opens the data base connection.
     * First it create the path up till data base of the device.
     * Then create connection with data base.
     */
    @Throws(SQLException::class)
    fun openDataBase() {
        //Open the database
        val myPath = DB_PATH + DATABASE_NAME
        sqliteDataBase = SQLiteDatabase.openDatabase(myPath, null, SQLiteDatabase.OPEN_READWRITE)
    }

    /**
     * This Method is used to close the data base connection.
     */
    @Synchronized
    override fun close() {
        if (sqliteDataBase != null) sqliteDataBase!!.close()
        super.close()
    }

    override fun onCreate(db: SQLiteDatabase) {
        // No need to write the create table query.
        // As we are using Pre built data base.
        // Which is ReadOnly.
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // No need to write the update table query.
        // As we are using Pre built data base.
        // Which is ReadOnly.
        // We should not update it as requirements of application.
    }

    companion object {
        //The Android's default system path of your application database.
        private const val DB_PATH = "/data/data/com.yomi.yomi/databases/"

        // Data Base Name.
        private const val DATABASE_NAME = "DB.db"

        // Data Base Version.
        private const val DATABASE_VERSION = 1

        // Table Names of Data Base.
        const val TABLE_entryoptimized = "entryoptimized"
        var sqliteDataBase: SQLiteDatabase? = null
    }
}