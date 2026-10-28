package com.duorhythm.app.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import com.duorhythm.app.databinding.DialogCropAvatarBinding
import com.duorhythm.app.util.AnimationUtils
import com.duorhythm.app.util.ImageUtils

object CropAvatarDialog {

    fun show(
        context: Context,
        sampledBitmap: Bitmap,
        onCropped: (Bitmap) -> Unit
    ) {
        val binding = DialogCropAvatarBinding.inflate(LayoutInflater.from(context))
        val dialog = AlertDialog.Builder(context)
            .setView(binding.root)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        AnimationUtils.applyDialogAnimation(dialog)

        binding.cropImageView.setSourceBitmap(sampledBitmap)

        binding.btnCropReset.setOnClickListener {
            binding.cropImageView.resetZoom()
        }

        binding.btnCropCancel.setOnClickListener {
            dialog.dismiss()
        }

        binding.btnCropApply.setOnClickListener {
            try {
                val cropBounds = binding.cropOverlayView.getCropCircleBounds()
                val cropped = binding.cropImageView.cropCircular(cropBounds, targetSize = 512)
                    ?: ImageUtils.cropToCircle(sampledBitmap, targetSize = 512)

                onCropped(cropped)
                dialog.dismiss()
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(context, "Error cropping avatar: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.setOnDismissListener {
            try {
                if (!sampledBitmap.isRecycled) {
                    sampledBitmap.recycle()
                }
            } catch (_: Exception) {}
        }

        dialog.show()
    }
}
