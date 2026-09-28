package com.example.tapthetarget

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Random

class MainActivity : AppCompatActivity() {

    // ==== 1. Data permainan standar ====
    private var skor = 0
    private val durasiPermainan = 30_000L
    private var sisaWaktuDetik = 30
    private var timer: CountDownTimer? = null
    private var sedangDijeda = false
    private val acak = Random()

    // ==== 2. Data fitur opsional ====
    private var klikSukses = 0
    private var skorPerKlik = 10
    private var ukuranTargetDp = 72
    private var toneGen: ToneGenerator? = null

    // Handler untuk logika target menghilang otomatis
    private val handlerTarget = Handler(Looper.getMainLooper())
    private val sembunyiRunnable = Runnable {
        imgTarget.visibility = View.INVISIBLE
        handlerTarget.postDelayed(munculRunnable, 1000L) // Hilang selama 1 detik
    }
    private val munculRunnable = Runnable {
        if (!sedangDijeda && sisaWaktuDetik > 0) {
            pindahkanTarget()
            imgTarget.visibility = View.VISIBLE
            mulaiTimeoutTarget() // Mulai hitung mundur 2 detik lagi
        }
    }

    // ==== 3. View ====
    private lateinit var frameGame: FrameLayout
    private lateinit var imgBackground: ImageView
    private lateinit var imgTarget: ImageView
    private lateinit var tvSkor: TextView
    private lateinit var tvWaktu: TextView
    private lateinit var btnPause: Button
    private lateinit var overlayPause: LinearLayout
    private lateinit var overlayGameOver: LinearLayout
    private lateinit var tvSkorJeda: TextView
    private lateinit var tvSkorAkhir: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        frameGame = findViewById(R.id.frameGame)
        imgBackground = findViewById(R.id.imgBackground) // ID untuk ubah latar
        imgTarget = findViewById(R.id.imgTarget)
        tvSkor = findViewById(R.id.tvSkor)
        tvWaktu = findViewById(R.id.tvWaktu)
        btnPause = findViewById(R.id.btnPause)
        overlayPause = findViewById(R.id.overlayPause)
        overlayGameOver = findViewById(R.id.overlayGameOver)
        tvSkorJeda = findViewById(R.id.tvSkorJeda)
        tvSkorAkhir = findViewById(R.id.tvSkorAkhir)

        // Fitur Efek Suara: Siapkan ToneGenerator
        toneGen = ToneGenerator(AudioManager.STREAM_MUSIC, 100)

        imgTarget.setOnClickListener { targetDitekan() }
        btnPause.setOnClickListener { jedaPermainan() }
        findViewById<Button>(R.id.btnLanjutkan).setOnClickListener { lanjutkanPermainan() }
        findViewById<Button>(R.id.btnUlangi).setOnClickListener { mulaiPermainan() }
        findViewById<Button>(R.id.btnMainLagi).setOnClickListener { mulaiPermainan() }

        // Fitur Tahan Rotasi: Cek apakah ada state yang diselamatkan
        if (savedInstanceState != null) {
            pulihkanState(savedInstanceState)
        } else {
            mulaiPermainan()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        timer?.cancel()
        batalkanTimeout()
        toneGen?.release() // Bebaskan memori efek suara
    }

    // ================= MEMULAI PERMAINAN =================
    private fun mulaiPermainan() {
        skor = 0
        sisaWaktuDetik = 30
        sedangDijeda = false

        // Reset fitur tingkat kesulitan
        klikSukses = 0
        skorPerKlik = 10
        ukuranTargetDp = 72
        terapkanUkuranTarget()

        // Fitur Latar Acak
        val pilihanLatar = arrayOf(R.drawable.bg_game, R.drawable.bg_game_2, R.drawable.bg_game_3)
        imgBackground.setImageResource(pilihanLatar.random())

        tvSkor.text = getString(R.string.label_skor, skor)
        tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)

        overlayPause.visibility = View.GONE
        overlayGameOver.visibility = View.GONE
        overlayGameOver.alpha = 1f

        imgTarget.visibility = View.VISIBLE
        imgTarget.isEnabled = true
        imgTarget.scaleX = 1f
        imgTarget.scaleY = 1f
        btnPause.isEnabled = true

        frameGame.post { pindahkanTarget() }
        jalankanTimer(durasiPermainan)
        mulaiTimeoutTarget()
    }

    // ================= LOGIKA TIMEOUT TARGET =================
    private fun mulaiTimeoutTarget() {
        batalkanTimeout()
        // Jika 2 detik tidak dipencet, jalankan sembunyiRunnable
        handlerTarget.postDelayed(sembunyiRunnable, 2000L)
    }

    private fun batalkanTimeout() {
        handlerTarget.removeCallbacks(sembunyiRunnable)
        handlerTarget.removeCallbacks(munculRunnable)
    }

    // ================= LOGIKA UKURAN & POSISI =================
    private fun terapkanUkuranTarget() {
        val px = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, ukuranTargetDp.toFloat(), resources.displayMetrics).toInt()
        imgTarget.layoutParams.width = px
        imgTarget.layoutParams.height = px
        imgTarget.requestLayout()
    }

    private fun pindahkanTarget() {
        val lebarArea = frameGame.width
        val tinggiArea = frameGame.height
        if (lebarArea == 0 || tinggiArea == 0) return

        val jarakAman = 24
        val maxX = lebarArea - imgTarget.width - (jarakAman * 2)
        val maxY = tinggiArea - imgTarget.height - (jarakAman * 2)
        if (maxX <= 0 || maxY <= 0) return

        imgTarget.x = (jarakAman + acak.nextInt(maxX)).toFloat()
        imgTarget.y = (jarakAman + acak.nextInt(maxY)).toFloat()
    }

    // ================= KLIK TARGET =================
    private fun targetDitekan() {
        if (sedangDijeda) return

        batalkanTimeout() // Batal menghilang karena berhasil ditekan
        toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 100) // Fitur Efek Suara

        klikSukses++
        skor += skorPerKlik

        // Fitur Tingkat Kesulitan: Setiap 5 klik, skor naik & ukuran mengecil
        if (klikSukses % 5 == 0) {
            skorPerKlik += 10
            if (ukuranTargetDp > 40) {
                ukuranTargetDp -= 8
                terapkanUkuranTarget()
            }
        }

        tvSkor.text = getString(R.string.label_skor, skor)

        imgTarget.animate()
            .scaleX(0.6f).scaleY(0.6f).setDuration(70)
            .withEndAction {
                imgTarget.animate().scaleX(1f).scaleY(1f).setDuration(70).start()
            }
            .start()

        pindahkanTarget()
        mulaiTimeoutTarget() // Restart timer menghilang
    }

    // ================= TIMER & JEDA =================
    private fun jalankanTimer(durasi: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(durasi, 1_000L) {
            override fun onTick(millisUntilFinished: Long) {
                sisaWaktuDetik = (millisUntilFinished / 1000).toInt()
                tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
            }
            override fun onFinish() {
                sisaWaktuDetik = 0
                tvWaktu.text = getString(R.string.label_waktu, 0)
                gameOver()
            }
        }.start()
    }

    private fun jedaPermainan() {
        if (sedangDijeda || overlayGameOver.visibility == View.VISIBLE) return
        sedangDijeda = true
        timer?.cancel()
        batalkanTimeout()
        imgTarget.isEnabled = false

        tvSkorJeda.text = skor.toString()
        overlayPause.alpha = 0f
        overlayPause.visibility = View.VISIBLE
        overlayPause.animate().alpha(1f).setDuration(200).start()
    }

    private fun lanjutkanPermainan() {
        if (!sedangDijeda) return
        sedangDijeda = false
        overlayPause.visibility = View.GONE

        // Cek apakah target sedang dalam fase tak terlihat
        if (imgTarget.visibility == View.VISIBLE) {
            imgTarget.isEnabled = true
            mulaiTimeoutTarget()
        }
        jalankanTimer(sisaWaktuDetik * 1000L)
    }

    // ================= GAME OVER & HIGHSCORE =================
    private fun gameOver() {
        timer?.cancel()
        batalkanTimeout()
        sedangDijeda = false

        imgTarget.isEnabled = false
        imgTarget.visibility = View.GONE
        btnPause.isEnabled = false
        overlayPause.visibility = View.GONE

        // Fitur Highscore menggunakan SharedPreferences
        val prefs = getSharedPreferences("GAME_DATA", Context.MODE_PRIVATE)
        val highscore = prefs.getInt("HIGHSCORE", 0)

        tvSkorAkhir.textSize = 28f // Kecilkan sedikit agar muat 2 baris
        if (skor > highscore) {
            prefs.edit().putInt("HIGHSCORE", skor).apply()
            tvSkorAkhir.text = "Skor: $skor\nRekor Baru!"
        } else {
            tvSkorAkhir.text = "Skor: $skor\nTerbaik: $highscore"
        }

        overlayGameOver.alpha = 0f
        overlayGameOver.visibility = View.VISIBLE
        overlayGameOver.animate().alpha(1f).setDuration(350).start()
    }

    // ================= ROTASI LAYAR (SAVE & RESTORE) =================
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("SKOR", skor)
        outState.putInt("WAKTU", sisaWaktuDetik)
        outState.putBoolean("JEDA", sedangDijeda)
        outState.putInt("KLIK", klikSukses)
        outState.putInt("UKURAN", ukuranTargetDp)
        outState.putInt("SKOR_KLIK", skorPerKlik)
        outState.putInt("OVL_JEDA", overlayPause.visibility)
        outState.putInt("OVL_OVER", overlayGameOver.visibility)
    }

    private fun pulihkanState(saved: Bundle) {
        skor = saved.getInt("SKOR")
        sisaWaktuDetik = saved.getInt("WAKTU")
        sedangDijeda = saved.getBoolean("JEDA")
        klikSukses = saved.getInt("KLIK")
        ukuranTargetDp = saved.getInt("UKURAN")
        skorPerKlik = saved.getInt("SKOR_KLIK")

        tvSkor.text = getString(R.string.label_skor, skor)
        tvWaktu.text = getString(R.string.label_waktu, sisaWaktuDetik)
        terapkanUkuranTarget()

        val ovlJeda = saved.getInt("OVL_JEDA")
        val ovlOver = saved.getInt("OVL_OVER")
        overlayPause.visibility = ovlJeda
        overlayGameOver.visibility = ovlOver

        if (ovlOver == View.VISIBLE) {
            imgTarget.visibility = View.GONE
            btnPause.isEnabled = false
            // Tulis ulang highscore agar tidak hilang
            val highscore = getSharedPreferences("GAME_DATA", Context.MODE_PRIVATE).getInt("HIGHSCORE", 0)
            tvSkorAkhir.textSize = 28f
            tvSkorAkhir.text = if(skor >= highscore) "Skor: $skor\nRekor Baru!" else "Skor: $skor\nTerbaik: $highscore"
        } else if (ovlJeda == View.VISIBLE) {
            tvSkorJeda.text = skor.toString()
            imgTarget.isEnabled = false
        } else {
            imgTarget.visibility = View.VISIBLE
            imgTarget.isEnabled = true
            btnPause.isEnabled = true
            frameGame.post { pindahkanTarget() }
            jalankanTimer(sisaWaktuDetik * 1000L)
            mulaiTimeoutTarget()
        }
    }
}