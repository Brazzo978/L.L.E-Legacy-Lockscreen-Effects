package com.codex.lle;

import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/** Bottom-sheet emoji chooser for the hidden Emoji Trail effect. */
final class EmojiTrailPickerDialog {
    private static final int GREEN = Color.rgb(0, 168, 132);
    private static final int INK = Color.rgb(35, 45, 48);
    private static final int MUTED = Color.rgb(100, 112, 116);
    private static final int PANEL = Color.rgb(247, 249, 250);
    private static final int LINE = Color.rgb(227, 232, 234);
    private static final String RECENT_KEY = "emoji_trail_recent";

    private static final String[] CATEGORY_NAMES = {
            "Recent", "Smileys & people", "Animals & nature", "Food & drink",
            "Activities", "Travel & places", "Objects", "Symbols", "Flags"
    };
    private static final String[] CATEGORY_ICONS = {
            "◷", "☺", "🐻", "🍕", "⚽", "🚗", "💡", "♥", "⚑"
    };
    private static final String[] CATEGORY_EMOJIS = {
            "",
            "😀 😃 😄 😁 😆 😅 😂 🤣 🥲 ☺️ 😊 😇 🙂 🙃 😉 😌 😍 🥰 😘 😗 😙 😚 😋 😛 😝 😜 🤪 🤨 🧐 🤓 😎 🥸 🤩 🥳 🙂‍↕️ 😏 😒 🙂‍↔️ 😞 😔 😟 😕 🙁 ☹️ 😣 😖 😫 😩 🥺 😢 😭 😤 😠 😡 🤬 🤯 😳 🥵 🥶 😱 😨 😰 😥 😓 🤗 🤔 🫢 🤭 🥱 🤫 🤥 😶 😶‍🌫️ 😐 😑 😬 🫨 🙄 😯 😮 😲 🥱 😴 🤤 😪 😵 😵‍💫 🫠 🤑 🤠 😈 👿 👹 👺 🤡 💩 👻 💀 ☠️ 👽 👾 🤖 🎃 😺 😸 😹 😻 😼 😽 🙀 😿 😾 👋 🤚 🖐️ ✋ 🖖 🫱 🫲 🫳 🫴 🫶 👌 🤌 🤏 ✌️ 🤞 🫰 🤟 🤘 🤙 👈 👉 👆 🖕 👇 ☝️ 👍 👎 ✊ 👊 🤛 🤜 👏 🙌 👐 🤲 🤝 🙏 ✍️ 💅 🤳 💪 🦾 🦿 🦵 🦶 👂 🦻 👃 🧠 🫀 🫁 🦷 🦴 👀 👁️ 👅 👄 🫦 👶 🧒 👦 👧 🧑 👨 👩 🧓 👴 👵 🙍 🙎 🙅 🙆 💁 🙋 🧏 🙇 🤦 🤷 👮 🕵️ 💂 🥷 👷 🫅 🤴 👸 👳 🧕 🤵 👰 🤰 🫄 🤱 👼 🎅 🤶 🦸 🦹 🧙 🧚 🧛 🧜 🧝 🧞 🧟 💃 🕺 🕴️ 🚶 🏃 👯 🧖 🧘 🛀 🛌 👭 👬 👫 💏 💑 👪",
            "🐶 🐱 🐭 🐹 🐰 🦊 🐻 🐼 🐻‍❄️ 🐨 🐯 🦁 🐮 🐷 🐽 🐸 🐵 🙈 🙉 🙊 🐒 🐔 🐧 🐦 🐤 🐣 🐥 🦆 🦅 🦉 🦇 🐺 🐗 🐴 🦄 🐝 🪱 🐛 🦋 🐌 🐞 🐜 🪰 🪲 🪳 🦟 🦗 🕷️ 🕸️ 🦂 🐢 🐍 🦎 🦖 🦕 🐙 🦑 🦐 🦞 🦀 🐡 🐠 🐟 🐬 🐳 🐋 🦈 🐊 🐅 🐆 🦓 🦍 🦧 🦣 🐘 🦛 🦏 🐪 🐫 🦒 🦘 🦬 🐃 🐂 🐄 🐎 🐖 🐏 🐑 🦙 🐐 🦌 🐕 🐩 🦮 🐕‍🦺 🐈 🐈‍⬛ 🪶 🐓 🦃 🦚 🦜 🦢 🦩 🕊️ 🐇 🦝 🦨 🦡 🦫 🦦 🦥 🐁 🐀 🐿️ 🦔 🐾 🐉 🐲 🌵 🎄 🌲 🌳 🌴 🪵 🌱 🌿 ☘️ 🍀 🎍 🪴 🎋 🍃 🍂 🍁 🍄 🐚 🪸 🪨 🌾 💐 🌷 🌹 🥀 🌺 🌸 🌼 🌻 🌞 🌝 🌛 🌜 🌚 🌕 🌖 🌗 🌘 🌑 🌒 🌓 🌔 🌙 🌎 🌍 🌏 🪐 💫 ⭐ 🌟 ✨ ⚡ ☄️ 💥 🔥 🌪️ 🌈 ☀️ 🌤️ ⛅ 🌥️ ☁️ 🌦️ 🌧️ ⛈️ 🌩️ 🌨️ ❄️ ☃️ ⛄ 🌬️ 💨 💧 💦 ☔ 🌊",
            "🍏 🍎 🍐 🍊 🍋 🍋‍🟩 🍌 🍉 🍇 🍓 🫐 🍈 🍒 🍑 🥭 🍍 🥥 🥝 🍅 🍆 🥑 🥦 🥬 🥒 🌶️ 🫑 🌽 🥕 🫒 🧄 🧅 🥔 🍠 🫘 🥜 🌰 🫚 🫛 🍞 🥐 🥖 🫓 🥨 🥯 🥞 🧇 🧀 🍖 🍗 🥩 🥓 🍔 🍟 🍕 🌭 🥪 🌮 🌯 🫔 🥙 🧆 🥚 🍳 🥘 🍲 🫕 🥣 🥗 🍿 🧈 🧂 🥫 🍱 🍘 🍙 🍚 🍛 🍜 🍝 🍣 🍤 🍥 🥮 🍡 🥟 🥠 🥡 🦪 🍦 🍧 🍨 🍩 🍪 🎂 🍰 🧁 🥧 🍫 🍬 🍭 🍮 🍯 🍼 🥛 ☕ 🫖 🍵 🧃 🥤 🧋 🍶 🍺 🍻 🥂 🍷 🥃 🍸 🍹 🧉 🍾 🧊 🥄 🍴 🍽️",
            "⚽ 🏀 🏈 ⚾ 🥎 🎾 🏐 🏉 🥏 🎱 🪀 🏓 🏸 🏒 🏑 🥍 🏏 🪃 🥅 ⛳ 🪁 🏹 🎣 🤿 🥊 🥋 🎽 🛹 🛼 🛷 ⛸️ 🥌 🎿 ⛷️ 🏂 🪂 🏋️ 🤼 🤸 ⛹️ 🤺 🤾 🏌️ 🏇 🧘 🏄 🏊 🤽 🚣 🧗 🚴 🚵 🎖️ 🏆 🏅 🥇 🥈 🥉 🎯 🎳 🎮 🕹️ 🎰 🧩 ♟️ 🎲 🎭 🎨 🧵 🪡 🧶 🎪 🎟️ 🎫 🎬 🎤 🎧 🎼 🎹 🥁 🪘 🎷 🎺 🪗 🎸 🪕 🎻 🪈 🎉 🎊 🎈 🎂 🎁 🎀 🪩",
            "🚗 🚕 🚙 🚌 🚎 🏎️ 🚓 🚑 🚒 🚐 🛻 🚚 🚛 🚜 🏍️ 🛵 🚲 🛴 🚨 🚔 🚍 🚘 🚖 🚡 🚠 🚟 🚃 🚋 🚞 🚝 🚄 🚅 🚈 🚂 🚆 🚇 🚊 🚉 ✈️ 🛫 🛬 🛩️ 💺 🚁 🚀 🛸 🛰️ ⛵ 🛶 🚤 🛥️ 🛳️ ⛴️ 🚢 ⚓ 🛟 🗺️ 🗿 🗽 🗼 🏰 🏯 🏟️ 🎡 🎢 🎠 ⛲ ⛱️ 🏖️ 🏝️ 🏜️ 🌋 ⛰️ 🏔️ 🗻 🏕️ ⛺ 🏠 🏡 🏘️ 🏚️ 🏗️ 🏭 🏢 🏬 🏣 🏤 🏥 🏦 🏨 🏪 🏫 🏩 💒 ⛪ 🕌 🛕 🕍 ⛩️ 🕋 🌁 🌃 🏙️ 🌄 🌅 🌆 🌇 🌉 🌌 🌠 🎇 🎆",
            "⌚ 📱 💻 ⌨️ 🖥️ 🖨️ 🖱️ 🖲️ 💽 💾 💿 📀 📷 📸 📹 🎥 📽️ 📺 📻 🎙️ 🎚️ 🎛️ 🧭 ⏱️ ⏰ ⏲️ 🕰️ ⌛ ⏳ 📡 🔋 🪫 🪙 💡 🔦 🕯️ 🪔 🧯 🛢️ 💸 💵 💴 💶 💷 💰 💳 💎 ⚖️ 🪜 🧰 🪛 🔧 🔨 ⚒️ 🛠️ ⛏️ 🪚 🔩 ⚙️ 🧱 ⛓️ 🧲 🔫 🧨 💣 🪓 🔪 🗡️ ⚔️ 🛡️ 🚬 ⚰️ 🪦 ⚱️ 🏺 🔮 📿 🧿 🪬 💈 ⚗️ 🔭 🔬 🕳️ 🩹 🩺 💊 💉 🩸 🧬 🦠 🧫 🧪 🌡️ 🧹 🪠 🧺 🧻 🚽 🚿 🛁 🪥 🪒 🧽 🪣 🧴 🛎️ 🔑 🗝️ 🚪 🪑 🛋️ 🛏️ 🪞 🪟 🛍️ 🛒 🎒 👓 🕶️ 🥽 🥼 🦺 👔 👕 👖 🧣 🧤 🧥 🧦 👗 👘 🥻 🩱 👙 👚 👛 👜 👝 👒 🎩 🧢 👑 🪖 🎓 ⛑️ 📮 ✉️ 📩 📨 📧 💌 📥 📤 📦 🏷️ 📃 📜 📄 📑 🧾 📊 📈 📉 🗓️ 📆 🗒️ 📚 📖 🔖 🧷 📎 🖇️ 📐 📏 ✂️ 🖊️ 🖌️ 🖍️ 📝 ✏️ 🔍 🔎 🔒 🔓",
            "❤️ 🧡 💛 💚 💙 💜 🖤 🤍 🤎 🩷 🩵 🩶 💔 ❤️‍🔥 ❤️‍🩹 ❣️ 💕 💞 💓 💗 💖 💘 💝 💟 ☮️ ✝️ ☪️ 🕉️ ☸️ ✡️ 🔯 🕎 ☯️ ☦️ 🛐 ⛎ ♈ ♉ ♊ ♋ ♌ ♍ ♎ ♏ ♐ ♑ ♒ ♓ 🆔 ⚛️ 🉑 ☢️ ☣️ 📴 📳 ✴️ 🆚 💮 🉐 ㊙️ ㊗️ 🈴 🈵 🈹 🈲 🅰️ 🅱️ 🆎 🆑 🅾️ 🆘 ❌ ⭕ 🛑 ⛔ 📛 🚫 💯 💢 ♨️ 🚷 🚯 🚳 🚱 🔞 📵 ⚠️ 🚸 🔱 ⚜️ 🔰 ♻️ ✅ 🈯 💹 ❇️ ✳️ ❎ 🌐 💠 🌀 💤 🏧 🚾 ♿ 🅿️ 🛗 🈳 🈂️ 🛂 🛃 🛄 🛅 🚹 🚺 🚼 ⚧️ 🚻 🚮 🎦 📶 🈁 🔣 ℹ️ 🔤 🔡 🔠 🆖 🆗 🆙 🆒 🆕 🆓 0️⃣ 1️⃣ 2️⃣ 3️⃣ 4️⃣ 5️⃣ 6️⃣ 7️⃣ 8️⃣ 9️⃣ 🔟 🔢 #️⃣ *️⃣ ⏏️ ▶️ ⏸️ ⏯️ ⏹️ ⏺️ ⏭️ ⏮️ ⏩ ⏪ 🔀 🔁 🔂 ➕ ➖ ➗ ✖️ 🟰 ❓ ❔ ❕ ❗ ‼️ ⁉️ 💬 🗨️ 🗯️ 💭 🕳️ ♠️ ♥️ ♦️ ♣️ 🃏 🎴 🔇 🔈 🔉 🔊 🔔 🔕 🎵 🎶 🎼 🔴 🟠 🟡 🟢 🔵 🟣 ⚫ ⚪ 🟤 🟥 🟧 🟨 🟩 🟦 🟪 ⬛ ⬜ 🟫",
            "🏳️ 🏴 🏁 🚩 🎌 🏳️‍🌈 🏳️‍⚧️ 🇺🇳 🇪🇺 🇮🇹 🇬🇧 🇺🇸 🇨🇦 🇦🇺 🇳🇿 🇫🇷 🇩🇪 🇪🇸 🇵🇹 🇳🇱 🇧🇪 🇨🇭 🇦🇹 🇮🇪 🇬🇷 🇨🇾 🇹🇷 🇵🇱 🇨🇿 🇸🇰 🇭🇺 🇷🇴 🇧🇬 🇭🇷 🇸🇮 🇷🇸 🇺🇦 🇷🇺 🇸🇪 🇳🇴 🇩🇰 🇫🇮 🇮🇸 🇯🇵 🇰🇷 🇨🇳 🇹🇼 🇭🇰 🇮🇳 🇵🇰 🇧🇩 🇳🇵 🇱🇰 🇮🇩 🇲🇾 🇸🇬 🇹🇭 🇻🇳 🇵🇭 🇦🇪 🇸🇦 🇮🇱 🇪🇬 🇿🇦 🇳🇬 🇰🇪 🇲🇦 🇧🇷 🇦🇷 🇨🇱 🇲🇽 🇨🇴 🇵🇪"
    };

    private final Activity activity;
    private final Runnable onChanged;
    private final ArrayList<String> selected;
    private final ArrayList<String> recent = new ArrayList<String>();
    private Dialog dialog;
    private LinearLayout selectedRow;
    private LinearLayout emojiGrid;
    private LinearLayout categoryBar;
    private ScrollView emojiScroll;
    private TextView counter;
    private EditText search;
    private int category = 0;

    static void show(Activity activity, Runnable onChanged) {
        new EmojiTrailPickerDialog(activity, onChanged).show();
    }

    private EmojiTrailPickerDialog(Activity activity, Runnable onChanged) {
        this.activity = activity;
        this.onChanged = onChanged;
        selected = new ArrayList<String>(OverlayPrefs.emojiTrailEmojis(activity));
        SharedPreferences prefs = activity.getSharedPreferences("lle_emoji_picker", Context.MODE_PRIVATE);
        String saved = prefs.getString(RECENT_KEY, "");
        if (saved != null && saved.length() > 0) {
            for (String emoji : saved.split("\\|")) {
                if (emoji.length() > 0 && !recent.contains(emoji)) {
                    recent.add(emoji);
                }
            }
        }
        for (String emoji : selected) {
            if (!recent.contains(emoji)) {
                recent.add(0, emoji);
            }
        }
        if (recent.isEmpty()) {
            category = 1;
        }
    }

    private void show() {
        dialog = new Dialog(activity);
        LinearLayout root = new LinearLayout(activity);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(PANEL, 20));

        LinearLayout header = horizontal();
        header.setPadding(dp(18), dp(18), dp(14), dp(10));
        TextView title = label("Choose up to 6 emojis", 17, INK);
        title.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(30), 1f));
        counter = label("", 13, MUTED);
        counter.setGravity(Gravity.CENTER_VERTICAL | Gravity.RIGHT);
        header.addView(counter, new LinearLayout.LayoutParams(dp(44), dp(30)));
        TextView done = label("DONE", 13, GREEN);
        done.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        done.setGravity(Gravity.CENTER);
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dialog.dismiss();
            }
        });
        header.addView(done, new LinearLayout.LayoutParams(dp(56), dp(30)));
        root.addView(header);

        HorizontalScrollView selectedScroll = new HorizontalScrollView(activity);
        selectedScroll.setHorizontalScrollBarEnabled(false);
        selectedScroll.setFillViewport(true);
        selectedScroll.setPadding(dp(12), 0, dp(12), dp(8));
        selectedRow = horizontal();
        selectedScroll.addView(selectedRow);
        root.addView(selectedScroll, new LinearLayout.LayoutParams(-1, dp(62)));

        search = new EditText(activity);
        search.setSingleLine(true);
        search.setTextSize(15);
        search.setHint("⌕  Search emojis");
        search.setPadding(dp(14), 0, dp(14), 0);
        search.setBackground(rounded(Color.rgb(233, 237, 239), 24));
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, dp(43));
        searchParams.setMargins(dp(14), 0, dp(14), dp(7));
        root.addView(search, searchParams);

        emojiScroll = new ScrollView(activity);
        emojiScroll.setFillViewport(true);
        emojiGrid = new LinearLayout(activity);
        emojiGrid.setOrientation(LinearLayout.VERTICAL);
        emojiGrid.setPadding(dp(8), dp(4), dp(8), dp(4));
        emojiScroll.addView(emojiGrid);
        root.addView(emojiScroll, new LinearLayout.LayoutParams(-1, 0, 1f));

        View divider = new View(activity);
        divider.setBackgroundColor(LINE);
        root.addView(divider, new LinearLayout.LayoutParams(-1, dp(1)));
        HorizontalScrollView categories = new HorizontalScrollView(activity);
        categories.setHorizontalScrollBarEnabled(false);
        categoryBar = horizontal();
        categories.addView(categoryBar);
        root.addView(categories, new LinearLayout.LayoutParams(-1, dp(55)));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                renderGrid();
            }
            @Override public void afterTextChanged(Editable s) { }
        });

        renderSelection();
        renderCategories();
        renderGrid();
        dialog.setContentView(root);
        dialog.setOnDismissListener(new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface ignored) {
                onChanged.run();
            }
        });
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams params = window.getAttributes();
            params.width = WindowManager.LayoutParams.MATCH_PARENT;
            params.height = Math.min(dp(580), (int) (activity.getResources()
                    .getDisplayMetrics().heightPixels * 0.76f));
            params.gravity = Gravity.BOTTOM;
            params.dimAmount = 0.35f;
            window.setAttributes(params);
            window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE
                    | WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN);
        }
        dialog.show();
        if (window != null) {
            window.setLayout(WindowManager.LayoutParams.MATCH_PARENT,
                    Math.min(dp(580), (int) (activity.getResources()
                            .getDisplayMetrics().heightPixels * 0.76f)));
        }
    }

    private void renderSelection() {
        selectedRow.removeAllViews();
        counter.setText(selected.size() + "/" + OverlayPrefs.EMOJI_TRAIL_MAX_EMOJIS);
        for (int slot = 0; slot < OverlayPrefs.EMOJI_TRAIL_MAX_EMOJIS; slot++) {
            final String emoji = slot < selected.size() ? selected.get(slot) : null;
            TextView chip = label(emoji == null ? "+" : emoji, emoji == null ? 19 : 26,
                    emoji == null ? MUTED : INK);
            chip.setGravity(Gravity.CENTER);
            chip.setBackground(rounded(emoji == null ? Color.WHITE : Color.rgb(220, 247, 238), 15));
            chip.setContentDescription(emoji == null ? "Empty emoji slot"
                    : "Remove " + emoji);
            if (emoji != null) {
                chip.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        toggleEmoji(emoji);
                    }
                });
            }
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(48));
            params.setMargins(dp(3), 0, dp(3), 0);
            selectedRow.addView(chip, params);
        }
    }

    private void renderCategories() {
        categoryBar.removeAllViews();
        for (int index = 0; index < CATEGORY_NAMES.length; index++) {
            final int destination = index;
            TextView tab = label(CATEGORY_ICONS[index], 22, index == category ? GREEN : MUTED);
            tab.setGravity(Gravity.CENTER);
            tab.setContentDescription(CATEGORY_NAMES[index]);
            if (index == category) {
                tab.setBackground(rounded(Color.rgb(218, 243, 234), 17));
            }
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    category = destination;
                    search.setText("");
                    InputMethodManager input = (InputMethodManager) activity
                            .getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (input != null) {
                        input.hideSoftInputFromWindow(search.getWindowToken(), 0);
                    }
                    search.clearFocus();
                    emojiScroll.scrollTo(0, 0);
                    renderCategories();
                    renderGrid();
                }
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(42));
            params.setMargins(dp(2), dp(5), dp(2), dp(5));
            categoryBar.addView(tab, params);
        }
    }

    private void renderGrid() {
        final int previousScroll = emojiScroll.getScrollY();
        emojiGrid.removeAllViews();
        String query = search.getText().toString().trim().toLowerCase(Locale.ROOT);
        List<String> choices;
        String heading;
        if (query.length() > 0) {
            heading = "Search results";
            LinkedHashSet<String> matches = new LinkedHashSet<String>();
            for (int section = 1; section < CATEGORY_NAMES.length; section++) {
                for (String emoji : CATEGORY_EMOJIS[section].split(" ")) {
                    if (matchesQuery(emoji, section, query)) {
                        matches.add(emoji);
                    }
                }
            }
            for (String emoji : recent) {
                if (matchesQuery(emoji, 0, query)) {
                    matches.add(emoji);
                }
            }
            choices = new ArrayList<String>(matches);
        } else if (category == 0) {
            heading = "Recently used";
            choices = new ArrayList<String>(recent);
        } else {
            heading = CATEGORY_NAMES[category];
            choices = new ArrayList<String>();
            for (String emoji : CATEGORY_EMOJIS[category].split(" ")) {
                choices.add(emoji);
            }
        }
        TextView sectionLabel = label(heading, 13, MUTED);
        sectionLabel.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        sectionLabel.setPadding(dp(9), dp(6), 0, dp(8));
        emojiGrid.addView(sectionLabel);
        if (choices.isEmpty()) {
            TextView empty = label(category == 0 && query.length() == 0
                    ? "Your selected emojis will appear here." : "No matching emoji", 14, MUTED);
            empty.setGravity(Gravity.CENTER);
            emojiGrid.addView(empty, new LinearLayout.LayoutParams(-1, dp(100)));
        }
        for (int start = 0; start < choices.size(); start += 8) {
            LinearLayout row = horizontal();
            for (int index = start; index < start + 8; index++) {
                if (index >= choices.size()) {
                    row.addView(new View(activity), new LinearLayout.LayoutParams(0, dp(45), 1f));
                    continue;
                }
                final String emoji = choices.get(index);
                TextView cell = label(emoji, 27, INK);
                cell.setGravity(Gravity.CENTER);
                cell.setContentDescription((selected.contains(emoji) ? "Remove " : "Add ") + emoji);
                if (selected.contains(emoji)) {
                    cell.setBackground(rounded(Color.rgb(211, 241, 228), 13));
                }
                cell.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        toggleEmoji(emoji);
                    }
                });
                LinearLayout.LayoutParams cellParams = new LinearLayout.LayoutParams(0, dp(45), 1f);
                cellParams.setMargins(dp(1), dp(1), dp(1), dp(1));
                row.addView(cell, cellParams);
            }
            emojiGrid.addView(row);
        }
        emojiScroll.post(new Runnable() {
            @Override
            public void run() {
                emojiScroll.scrollTo(0, previousScroll);
            }
        });
    }

    private boolean matchesQuery(String emoji, int section, String query) {
        if (emoji.contains(query) || CATEGORY_NAMES[section].toLowerCase(Locale.ROOT).contains(query)) {
            return true;
        }
        for (int offset = 0; offset < emoji.length();) {
            int codePoint = emoji.codePointAt(offset);
            String name = Character.getName(codePoint);
            if (name != null && name.toLowerCase(Locale.ROOT).contains(query)) {
                return true;
            }
            offset += Character.charCount(codePoint);
        }
        return false;
    }

    private void toggleEmoji(String emoji) {
        if (selected.contains(emoji)) {
            selected.remove(emoji);
        } else {
            if (selected.size() >= OverlayPrefs.EMOJI_TRAIL_MAX_EMOJIS) {
                Toast.makeText(activity, "Choose up to 6 emojis", Toast.LENGTH_SHORT).show();
                return;
            }
            selected.add(emoji);
            recent.remove(emoji);
            recent.add(0, emoji);
            while (recent.size() > 32) {
                recent.remove(recent.size() - 1);
            }
            saveRecents();
        }
        OverlayPrefs.setEmojiTrailEmojis(activity, selected);
        renderSelection();
        renderGrid();
    }

    private void saveRecents() {
        StringBuilder value = new StringBuilder();
        for (String emoji : recent) {
            if (value.length() > 0) {
                value.append('|');
            }
            value.append(emoji);
        }
        activity.getSharedPreferences("lle_emoji_picker", Context.MODE_PRIVATE)
                .edit().putString(RECENT_KEY, value.toString()).apply();
    }

    private LinearLayout horizontal() {
        LinearLayout row = new LinearLayout(activity);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        return row;
    }

    private TextView label(String value, int sizeSp, int color) {
        TextView text = new TextView(activity);
        text.setText(value);
        text.setTextSize(sizeSp);
        text.setTextColor(color);
        return text;
    }

    private GradientDrawable rounded(int color, int radiusDp) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(dp(radiusDp));
        return drawable;
    }

    private int dp(int value) {
        return (int) (value * activity.getResources().getDisplayMetrics().density + 0.5f);
    }
}
