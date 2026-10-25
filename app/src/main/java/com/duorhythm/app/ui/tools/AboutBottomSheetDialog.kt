package com.duorhythm.app.ui.tools

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.duorhythm.app.databinding.DialogAboutLifesyncBinding

class AboutBottomSheetDialog(context: Context) : BottomSheetDialog(context) {

    private lateinit var binding: DialogAboutLifesyncBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DialogAboutLifesyncBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnCloseAbout.setOnClickListener {
            dismiss()
        }

        binding.btnLinkCentralHub.setOnClickListener {
            openUrl("https://taplink.cc/mi2007")
        }

        binding.btnLinkGithub.setOnClickListener {
            openUrl("https://github.com/MI-Musanna")
        }

        binding.btnLinkTelegram.setOnClickListener {
            openUrl("https://t.me/NetHunter2007")
        }

        binding.btnLinkBlog.setOnClickListener {
            openUrl("https://mitecharsenal.blogspot.com")
        }
    }

    private fun openUrl(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, "Could not open link: $url", Toast.LENGTH_SHORT).show()
        }
    }
}
