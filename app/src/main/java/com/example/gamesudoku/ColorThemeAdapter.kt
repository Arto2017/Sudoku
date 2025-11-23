package com.artashes.sudoku

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.TextView

data class ColorThemeItem(
    val name: String,
    val iconResId: Int,
    val theme: SudokuBoardView.ColorTheme
)

class ColorThemeAdapter(
    context: Context,
    private val themes: List<ColorThemeItem>
) : ArrayAdapter<ColorThemeItem>(context, 0, themes) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createItemView(position, convertView, parent)
    }

    private fun createItemView(position: Int, recycledView: View?, parent: ViewGroup): View {
        val item = getItem(position)
        val view = recycledView ?: LayoutInflater.from(context)
            .inflate(R.layout.spinner_item_color_theme, parent, false)

        val iconView = view.findViewById<ImageView>(R.id.colorIcon)
        val textView = view.findViewById<TextView>(R.id.themeName)
        
        item?.let {
            iconView.setImageResource(it.iconResId)
            textView.text = it.name
        }

        return view
    }

    companion object {
        fun createThemesList(): List<ColorThemeItem> {
            return listOf(
                ColorThemeItem("White", R.drawable.ic_color_white, SudokuBoardView.ColorTheme.WHITE),
                ColorThemeItem("Black", R.drawable.ic_color_black, SudokuBoardView.ColorTheme.BLACK),
                ColorThemeItem("Blue", R.drawable.ic_color_blue, SudokuBoardView.ColorTheme.STIFF_BLUE),
                ColorThemeItem("Yellow", R.drawable.ic_color_yellow, SudokuBoardView.ColorTheme.LIGHT_YELLOW)
            )
        }
    }
}
