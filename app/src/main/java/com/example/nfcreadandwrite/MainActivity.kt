package com.example.nfcreadandwrite

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.*
import android.nfc.tech.Ndef
import android.nfc.tech.NdefFormatable
import android.os.Bundle
import android.os.Parcelable
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.nfcreadandwrite.databinding.ActivityMainBinding
import java.io.IOException
import java.io.UnsupportedEncodingException
import java.nio.charset.Charset
import kotlin.experimental.and

class MainActivity : AppCompatActivity() {

    private lateinit var writeTagFilters: Array<IntentFilter>
    private lateinit var binding: ActivityMainBinding
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var writeMode = false
    private var myTag: Tag? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.apply {
            button.setOnClickListener {
                try {
                    if (myTag == null) {
                        Toast.makeText(this@MainActivity, ERROR_DETECTED, Toast.LENGTH_LONG).show()
                        Log.d("NFCCC", ERROR_DETECTED)
                    } else {
                        write(editMessage.text.toString(), myTag!!)
                        Toast.makeText(this@MainActivity, WRITE_SUCCESS, Toast.LENGTH_LONG).show()
                        Log.d("NFCCC", WRITE_SUCCESS)
                    }
                } catch (e: IOException) {
                    Toast.makeText(this@MainActivity, WRITE_ERROR, Toast.LENGTH_LONG).show()
                    Log.d("NFCCC", WRITE_ERROR)
                    e.printStackTrace()
                } catch (e: FormatException) {
                    Toast.makeText(this@MainActivity, WRITE_ERROR, Toast.LENGTH_LONG).show()
                    Log.d("NFCCC", WRITE_ERROR)
                    e.printStackTrace()
                }
            }
        }

        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (nfcAdapter == null) {
            Toast.makeText(this, "This device doesn't support NFC", Toast.LENGTH_LONG).show()
            Log.d("NFCCC", "This device doesn't support NFC")
            finish()
        }

        // for when the activity is launched by the intent-filter for android.nfc.action.NDEF_DISCOVERED
        readFromIntent(intent)
        pendingIntent = PendingIntent.getActivity(this,
            0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE)
        val tagDetected = IntentFilter(NfcAdapter.ACTION_TAG_DISCOVERED)
        tagDetected.addCategory(Intent.CATEGORY_DEFAULT)
        writeTagFilters = arrayOf(tagDetected)
    }

    /**
     * Read from NFC Tag
     */
    private fun readFromIntent(intent: Intent) {
        val action = intent.action
        if (NfcAdapter.ACTION_TAG_DISCOVERED == action || NfcAdapter.ACTION_TECH_DISCOVERED == action || NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
            val rawMsg = intent.getParcelableArrayExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, Parcelable::class.java)
            val msg = mutableListOf<NdefMessage>()
            if (rawMsg != null) {
                for (i in rawMsg.indices) {
                    msg.add(i, rawMsg[i] as NdefMessage)
                }
                buildTagViews(msg.toTypedArray())
            }
        }
    }

    private fun buildTagViews(msg: Array<NdefMessage>) {
        if (msg.isEmpty()) return
        var text = ""
        val payload = msg[0].records[0].payload
        val textEncoding: Charset =
            if ((payload[0] and 128.toByte()).toInt() == 0) Charsets.UTF_8 else Charsets.UTF_16 // get the text encoding
        val languageCodeLength: Int =
            (payload[0] and 51).toInt() // get the language code, e.g. "en"
        try {
            // get the text
            text = String(payload,
                languageCodeLength + 1,
                payload.size - languageCodeLength - 1,
                textEncoding)
            Log.d("TEST", "payload = $payload\npayloadSize = ${payload.size}\npayload[0] = ${payload[0]}\npayload[1] = ${payload[1]}\nlanguage code length = $languageCodeLength\ntext encoding = $textEncoding")
        } catch (e: UnsupportedEncodingException) {
            Log.e("UnsupportedEncoding", e.toString())
        }
        val outText = "Message read from NFC Tag:\n $text"
        binding.nfcContents.text = outText
    }

    /**
     * Write to NFC Tag
     */
    @Throws(IOException::class, FormatException::class)
    private fun write(text: String, tag: Tag) {
        val records = arrayOf(createRecord(text))
        val message = NdefMessage(records)

        val ndef = Ndef.get(tag) // <- get an instance of Ndef for the tag
        if (ndef != null) {
            ndef.apply {
                connect() // <- enable I/O
                writeNdefMessage(message) // <- write the message
                close() // <- close the connection
            }
        } else {
            val ndefFormatable = NdefFormatable.get(tag)
            if (ndefFormatable != null) {
                ndefFormatable.apply {
                    connect()
                    format(message)
                    close()
                }
            } else {
                Toast.makeText(this, "Wrong type of Tag!", Toast.LENGTH_LONG).show()
                Log.d("NFCCC", "Wrong type of Tag!")
            }
        }
    }

    @Throws(UnsupportedEncodingException::class)
    private fun createRecord(text: String): NdefRecord {
        val lang = "en"
        val textBytes = text.toByteArray()
        val langBytes = lang.toByteArray(charset("US-ASCII"))
        val langLength = langBytes.size
        val textLength = textBytes.size
        val payload = ByteArray(1 + langLength + textLength)

        // set status byte (see NDEF spec for actual bits)
        payload[0] = langLength.toByte()

        // copy langBytes and textBytes into payload
        System.arraycopy(langBytes, 0, payload, 1, langLength) // -> this function copies all elements of langBytes into payload starting from [1] index
        System.arraycopy(textBytes, 0, payload, 1 + langLength, textLength) // -> the function copies all elements of textBytes into payload starting from [1 + langLength] index
        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    /**
     * For reading the NFC when the app is already launched
     */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readFromIntent(intent)
//        val action = intent.action
//        if (NfcAdapter.ACTION_TAG_DISCOVERED == action || NfcAdapter.ACTION_TECH_DISCOVERED == action || NfcAdapter.ACTION_NDEF_DISCOVERED == action) {
//            myTag = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG, Tag::class.java)
//        }
    }

    override fun onPause() {
        super.onPause()
        writeModeOff()
    }

    override fun onResume() {
        super.onResume()
        writeModeOn()
    }

    /**
     * Enable Write and foreground dispatch to prevent intent-filter to launch the app again
     */
    private fun writeModeOn() {
        writeMode = true
        nfcAdapter!!.enableForegroundDispatch(this, pendingIntent, writeTagFilters, null)
    }

    /**
     * Disable Write and foreground dispatch to allow intent-filter to launch the app
     */
    private fun writeModeOff() {
        writeMode = false
        nfcAdapter!!.disableForegroundDispatch(this)
    }

    companion object {
        const val ERROR_DETECTED = "No NFC tag detected!"
        const val WRITE_SUCCESS = "Text written to the NFC tag successfully!"
        const val WRITE_ERROR = "Error during writing, is the NFC tag close enough to your device?"
    }
}