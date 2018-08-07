/* This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/. */

package org.mozilla.focus.searchsuggestions.ui

import android.arch.lifecycle.Observer
import android.arch.lifecycle.ViewModelProviders
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.support.v4.app.Fragment
import android.support.v4.content.ContextCompat
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.text.method.LinkMovementMethod
import android.text.style.ClickableSpan
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.fragment_search_suggestions.*

import org.mozilla.focus.R
import org.mozilla.focus.searchsuggestions.SearchSuggestionsViewModel
import org.mozilla.focus.searchsuggestions.State
import org.mozilla.focus.session.SessionManager
import org.mozilla.focus.session.Source
import org.mozilla.focus.utils.SupportUtils

class SearchSuggestionsFragment : Fragment() {
    private lateinit var searchSuggestionsViewModel: SearchSuggestionsViewModel

    override fun onResume() {
        super.onResume()

        searchSuggestionsViewModel.refresh()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        searchSuggestionsViewModel = ViewModelProviders.of(activity!!).get(SearchSuggestionsViewModel::class.java)
        searchSuggestionsViewModel.searchQuery.observe(this, Observer {
            searchView.text = it
        })

        searchSuggestionsViewModel.suggestions.observe(this, Observer { suggestions ->
            suggestions?.apply { (suggestionList.adapter as SuggestionsAdapter).refresh(this) }
        })

        searchSuggestionsViewModel.state.observe(this, Observer { state ->
            enable_search_suggestions_container.visibility = View.GONE
            no_suggestions_container.visibility = View.GONE
            suggestionList.visibility = View.GONE

            when (state) {
                is State.ReadyForSuggestions -> suggestionList.visibility = View.VISIBLE
                is State.NoSuggestionsAPI -> no_suggestions_container.visibility = if (state.givePrompt) View.VISIBLE else View.GONE
                is State.Disabled -> enable_search_suggestions_container.visibility = if (state.givePrompt) View.VISIBLE else View.GONE
            }
        })

        searchSuggestionsViewModel.searchEngine.observe(this, Observer { searchEngine ->
            val icon = searchEngine?.icon?.let { BitmapDrawable(resources, it) } ?: resources.getDrawable(R.drawable.ic_search, null)
            searchView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null)
        })
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? = inflater.inflate(R.layout.fragment_search_suggestions, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        enable_search_suggestions_subtitle.text = buildEnableSearchSuggestionsSubtitle()
        enable_search_suggestions_subtitle.movementMethod = LinkMovementMethod.getInstance()
        enable_search_suggestions_subtitle.highlightColor = Color.TRANSPARENT

        suggestionList.layoutManager = LinearLayoutManager(activity, LinearLayoutManager.VERTICAL, false)
        suggestionList.adapter = SuggestionsAdapter {
            searchSuggestionsViewModel.selectSearchSuggestion(it)
        }

        searchView.setOnClickListener {
            val textView = it
            if (textView is TextView) {
                searchSuggestionsViewModel.selectSearchSuggestion(textView.text.toString())
            }
        }

        enable_search_suggestions_button.setOnClickListener {
            searchSuggestionsViewModel.enableSearchSuggestions()
        }

        disable_search_suggestions_button.setOnClickListener {
            searchSuggestionsViewModel.disableSearchSuggestions()
        }

        dismiss_no_suggestions_message.setOnClickListener {
            searchSuggestionsViewModel.dismissNoSuggestionsMessage()
        }
    }

    private fun buildEnableSearchSuggestionsSubtitle(): SpannableString {
        val subtitle = resources.getString(R.string.enable_search_suggestion_subtitle)
        val appName = resources.getString(R.string.app_name)
        val learnMore = resources.getString(R.string.enable_search_suggestion_subtitle_learnmore)

        val spannable = SpannableString(String.format(subtitle, appName, learnMore))
        val startIndex = spannable.indexOf(learnMore)
        val endIndex = startIndex + learnMore.length

        val learnMoreSpan = object : ClickableSpan() {
            override fun onClick(textView: View) {
                val url = "https://mozilla.org"
                SessionManager.getInstance().createSession(Source.MENU, url)
            }

            override fun updateDrawState(ds: TextPaint?) {
                ds?.isUnderlineText = false
            }
        }

        spannable.setSpan(learnMoreSpan, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        val color = ForegroundColorSpan(ContextCompat.getColor(context!!, R.color.searchSuggestionPromptButtonTextColor))
        spannable.setSpan(color, startIndex, endIndex, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

        return spannable
    }

    companion object {
        fun create() = SearchSuggestionsFragment()
    }

    inner class SuggestionsAdapter(private val clickListener: (String) -> Unit): RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        private var suggestions: List<SpannableStringBuilder> = listOf()

        fun refresh(suggestions: List<SpannableStringBuilder>) {
            this.suggestions = suggestions
            notifyDataSetChanged()
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            if (holder is SuggestionViewHolder) {
                holder.bind(suggestions[position])
            }
        }

        override fun getItemCount(): Int = suggestions.count()

        override fun getItemViewType(position: Int): Int {
            return SuggestionViewHolder.LAYOUT_ID
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return SuggestionViewHolder(LayoutInflater.from(parent.context).inflate(viewType, parent, false), clickListener)
        }
    }
}


/**
 * ViewHolder implementation for a suggestion item in the list.
 */
private class SuggestionViewHolder(itemView: View, private val clickListener: (String) -> Unit) : RecyclerView.ViewHolder(itemView) {
    companion object {
        const val LAYOUT_ID = R.layout.item_suggestion
    }

    val suggestionText: TextView = itemView.findViewById(R.id.suggestion)

    fun bind(suggestion: SpannableStringBuilder) {
        suggestionText.text = suggestion
        itemView.setOnClickListener { clickListener(suggestion.toString()) }
    }
}