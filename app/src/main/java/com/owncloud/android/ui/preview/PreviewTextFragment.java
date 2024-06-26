/*
 * Nextcloud - Android Client
 *
 * SPDX-FileCopyrightText: 2019-2022 Tobias Kaminsky <tobias@kaminsky.me>
 * SPDX-FileCopyrightText: 2019-2022 Andy Scherzinger <info@andy-scherzinger.de>
 * SPDX-FileCopyrightText: 2019 Chris Narkiewicz <hello@ezaquarii.com>
 * SPDX-FileCopyrightText: 2016 ownCloud Inc.
 * SPDX-FileCopyrightText: 2014 Jorge Antonio Diaz-Benito Soriano <jorge.diazbenitosoriano@gmail.com>
 * SPDX-License-Identifier: GPL-2.0-only AND (AGPL-3.0-or-later OR GPL-2.0-only)
 */
package com.owncloud.android.ui.preview;

import android.app.Activity;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.text.Html;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.nextcloud.client.account.UserAccountManager;
import com.nextcloud.client.device.DeviceInfo;
import com.nextcloud.client.di.Injectable;
import com.owncloud.android.R;
import com.owncloud.android.databinding.TextFilePreviewBinding;
import com.owncloud.android.datamodel.OCFile;
import com.owncloud.android.lib.common.utils.Log_OC;
import com.owncloud.android.ui.activity.FileDisplayActivity;
import com.owncloud.android.ui.fragment.FileFragment;
import com.owncloud.android.utils.DisplayUtils;
import com.owncloud.android.utils.MimeTypeUtil;
import com.owncloud.android.utils.StringUtils;
import com.owncloud.android.utils.theme.ViewThemeUtils;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.SearchView;
import io.noties.markwon.AbstractMarkwonPlugin;
import io.noties.markwon.Markwon;
import io.noties.markwon.MarkwonConfiguration;
import io.noties.markwon.core.MarkwonTheme;
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin;
import io.noties.markwon.ext.tables.TablePlugin;
import io.noties.markwon.ext.tasklist.TaskListDrawable;
import io.noties.markwon.ext.tasklist.TaskListPlugin;
import io.noties.markwon.html.HtmlPlugin;
import io.noties.markwon.syntax.Prism4jTheme;
import io.noties.markwon.syntax.Prism4jThemeDefault;
import io.noties.markwon.syntax.SyntaxHighlightPlugin;
import io.noties.prism4j.Prism4j;
import io.noties.prism4j.annotations.PrismBundle;

@PrismBundle(
    include = {
        "c", "clike", "clojure", "cpp", "csharp", "css", "dart", "git", "go", "groovy", "java", "javascript", "json",
        "kotlin", "latex", "makefile", "markdown", "markup", "python", "scala", "sql", "swift", "yaml"
    },
    grammarLocatorClassName = ".MarkwonGrammarLocator"
)
public abstract class PreviewTextFragment extends FileFragment implements SearchView.OnQueryTextListener, Injectable {
    private static final String TAG = PreviewTextFragment.class.getSimpleName();

    protected SearchView searchView;
    protected String searchQuery = "";
    protected boolean searchOpen;
    protected Handler handler;
    protected String originalText;

    @Inject UserAccountManager accountManager;
    @Inject DeviceInfo deviceInfo;
    @Inject ViewThemeUtils viewThemeUtils;

    protected TextFilePreviewBinding binding;

    /**
     * {@inheritDoc}
     */
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        Log_OC.e(TAG, "onCreateView");

        binding = TextFilePreviewBinding.inflate(inflater, container, false);
        View view = binding.getRoot();

        binding.emptyListProgress.setVisibility(View.VISIBLE);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        Log_OC.e(TAG, "onStart");

        loadAndShowTextPreview();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    abstract void loadAndShowTextPreview();

    @Override
    public boolean onQueryTextSubmit(String query) {
        performSearch(query, 0);
        return true;
    }

    @Override
    public boolean onQueryTextChange(final String newText) {
        performSearch(newText, 500);
        return true;
    }


    private void performSearch(final String query, int delay) {
        handler.removeCallbacksAndMessages(null);

        if (originalText != null) {
            if (getActivity() instanceof FileDisplayActivity) {
                FileDisplayActivity fileDisplayActivity = (FileDisplayActivity) getActivity();
                fileDisplayActivity.setSearchQuery(query);
            }
            handler.postDelayed(() -> markText(query), delay);
        }

        if (delay == 0 && searchView != null) {
            searchView.clearFocus();
        }
    }

    private void markText(String query) {
        // called asynchronously - must check preconditions in case of UI detachment
        if (binding == null) {
            return;
        }
        final Activity activity = getActivity();
        if (activity == null) {
            return;
        }
        final Resources resources = activity.getResources();
        if (resources == null) {
            return;
        }

        if (!TextUtils.isEmpty(query)) {
            String coloredText = StringUtils.searchAndColor(originalText,
                                                            query,
                                                            resources.getColor(R.color.primary));
            binding.textPreview.setText(Html.fromHtml(coloredText.replace("\n", "<br \\>")));
        } else {
            setText(binding.textPreview, originalText, getFile(), activity, false, false, viewThemeUtils);
        }
    }

    protected static Spanned getRenderedMarkdownText(Activity activity,
                                                     String markdown,
                                                     ViewThemeUtils viewThemeUtils) {
        Prism4j prism4j = new Prism4j(new MarkwonGrammarLocator());
        Prism4jTheme prism4jTheme = Prism4jThemeDefault.create();
        TaskListDrawable drawable = new TaskListDrawable(Color.GRAY, Color.GRAY, Color.WHITE);
        viewThemeUtils.platform.tintPrimaryDrawable(activity, drawable);

        final Markwon markwon = Markwon.builder(activity)
            .usePlugin(new AbstractMarkwonPlugin() {
                @Override
                public void configureTheme(@NonNull MarkwonTheme.Builder builder) {
                    builder.linkColor(viewThemeUtils.platform.primaryColor(activity));
                    builder.headingBreakHeight(0);
                }

                @Override
                public void configureConfiguration(@NonNull MarkwonConfiguration.Builder builder) {
                    builder.linkResolver((view, link) -> DisplayUtils.startLinkIntent(activity, link));
                }
            })
            .usePlugin(TablePlugin.create(activity))
            .usePlugin(TaskListPlugin.create(drawable))
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(HtmlPlugin.create())
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, prism4jTheme))
            .build();

        return markwon.toMarkdown(markdown);
    }

    /**
     * Finishes the preview
     */
    protected void finish() {
        requireActivity().runOnUiThread(() -> requireActivity().onBackPressed());
    }

    public static void setText(TextView textView,
                               @Nullable String text,
                               @Nullable OCFile file,
                               Activity activity,
                               boolean ignoreMimetype,
                               boolean preview,
                               ViewThemeUtils viewThemeUtils) {
        if (text == null) {
            return;
        }

        if ((ignoreMimetype || file != null && MimeTypeUtil.MIMETYPE_TEXT_MARKDOWN.equals(file.getMimeType()))
            && activity != null) {
            if (!preview) {
                // clickable links prevent to open full view of rich workspace
                textView.setMovementMethod(LinkMovementMethod.getInstance());
            }
            textView.setText(getRenderedMarkdownText(activity, text, viewThemeUtils));
        } else {
            textView.setText(text);
        }
    }
}
