package com.factory.chemcalc;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {

    private static final String PREF_NAME = "ChemShiftPrefs";
    private static final String KEY_ITEMS = "chem_items_json";
    private static final String KEY_HISTORY = "chem_history_json";
    private static final String KEY_EARLY_H = "shift_early_h";
    private static final String KEY_MID_H = "shift_mid_h";
    private static final String KEY_NIGHT_H = "shift_night_h";
    private static final String KEY_DECIMALS = "decimal_places";
    private static final String KEY_INPUT_IS_KG = "input_unit_is_kg";

    // Dark Industrial Palette
    private static final int COL_BG_APP = Color.parseColor("#0B1120");
    private static final int COL_BG_CARD = Color.parseColor("#151F32");
    private static final int COL_BG_INPUT = Color.parseColor("#0D1526");
    private static final int COL_BG_ELEVATED = Color.parseColor("#1E293B");
    private static final int COL_BORDER = Color.parseColor("#263449");
    private static final int COL_CYAN = Color.parseColor("#38BDF8");
    private static final int COL_CYAN_TINT = Color.parseColor("#0C4A6E");
    private static final int COL_EMERALD = Color.parseColor("#10B981");
    private static final int COL_EMERALD_TINT = Color.parseColor("#064E3B");
    private static final int COL_BLUE = Color.parseColor("#0284C7");
    private static final int COL_AMBER = Color.parseColor("#F59E0B");
    private static final int COL_AMBER_TINT = Color.parseColor("#3B2809");
    private static final int COL_INDIGO = Color.parseColor("#818CF8");
    private static final int COL_RED = Color.parseColor("#DC2626");
    private static final int COL_TEXT_PRIMARY = Color.parseColor("#F8FAFC");
    private static final int COL_TEXT_MUTED = Color.parseColor("#94A3B8");

    private static class ChemItem {
        String name;
        String rawTotal;
        double sg; // Specific Gravity (比重)

        ChemItem(String name, String rawTotal, double sg) {
            this.name = name;
            this.rawTotal = rawTotal;
            this.sg = sg > 0 ? sg : 1.0;
        }

        double getTotalValue() {
            if (TextUtils.isEmpty(rawTotal)) return 0.0;
            try {
                return Double.parseDouble(rawTotal.trim());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
    }

    private static class CardViews {
        TextView txtSgPill;
        TextView txtHourly;
        TextView lblTotalUnit;
        TextView txtConvertedTotal;
        TextView txtEarlyTitle;
        TextView txtEarlyVal;
        TextView txtEarlySub;
        TextView txtMidTitle;
        TextView txtMidVal;
        TextView txtMidSub;
        TextView txtNightTitle;
        TextView txtNightVal;
        TextView txtNightSub;
        EditText edtTotal;
    }

    private SharedPreferences prefs;
    private final List<ChemItem> items = new ArrayList<>();
    private final List<CardViews> cardViewsList = new ArrayList<>();

    private double earlyHours = 8.5;
    private double midHours = 8.0;
    private double nightHours = 7.5;
    private int decimalPlaces = 2;
    private boolean inputIsKg = false; // false: 輸入 L → 換算 kg (×比重); true: 輸入 kg → 換算 L (÷比重)

    private TextView txtSubtitle;
    private TextView btnUnitMode;
    private LinearLayout layCardsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
        loadState();
        buildUI();
        rebuildCards();
    }

    private void loadState() {
        earlyHours = Double.longBitsToDouble(prefs.getLong(KEY_EARLY_H, Double.doubleToRawLongBits(8.5)));
        midHours = Double.longBitsToDouble(prefs.getLong(KEY_MID_H, Double.doubleToRawLongBits(8.0)));
        nightHours = Double.longBitsToDouble(prefs.getLong(KEY_NIGHT_H, Double.doubleToRawLongBits(7.5)));
        decimalPlaces = prefs.getInt(KEY_DECIMALS, 2);
        inputIsKg = prefs.getBoolean(KEY_INPUT_IS_KG, false);

        items.clear();
        String json = prefs.getString(KEY_ITEMS, null);
        if (!TextUtils.isEmpty(json)) {
            try {
                JSONArray arr = new JSONArray(json);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    String name = obj.getString("name");
                    double defaultSg = guessDefaultSg(name);
                    items.add(new ChemItem(
                            name,
                            obj.optString("total", ""),
                            obj.optDouble("sg", defaultSg)
                    ));
                }
            } catch (Exception ignored) {
            }
        }
        if (items.isEmpty()) {
            loadDefaultItems();
        }
    }

    private double guessDefaultSg(String name) {
        if (name == null) return 1.0;
        if (name.contains("硫酸")) return 1.84;
        if (name.contains("液鹼")) return 1.48;
        if (name.contains("氯化鐵")) return 1.40;
        if (name.contains("亞鐵")) return 1.25;
        if (name.contains("酸助劑") || name.contains("酸化")) return 1.15;
        if (name.contains("氯化銅")) return 1.35;
        return 1.00;
    }

    private void loadDefaultItems() {
        items.clear();
        items.add(new ChemItem("硫酸", "3632", 1.84));
        items.add(new ChemItem("液鹼", "4350", 1.48));
        items.add(new ChemItem("氯化鐵", "1505", 1.40));
        items.add(new ChemItem("亞鐵", "798", 1.25));
        items.add(new ChemItem("酸助劑", "920", 1.15));
        saveItems();
    }

    private void saveItems() {
        try {
            JSONArray arr = new JSONArray();
            for (ChemItem item : items) {
                JSONObject obj = new JSONObject();
                obj.put("name", item.name);
                obj.put("total", item.rawTotal == null ? "" : item.rawTotal);
                obj.put("sg", item.sg);
                arr.put(obj);
            }
            prefs.edit().putString(KEY_ITEMS, arr.toString()).apply();
        } catch (Exception ignored) {
        }
    }

    private void saveShiftSettings() {
        prefs.edit()
                .putLong(KEY_EARLY_H, Double.doubleToRawLongBits(earlyHours))
                .putLong(KEY_MID_H, Double.doubleToRawLongBits(midHours))
                .putLong(KEY_NIGHT_H, Double.doubleToRawLongBits(nightHours))
                .putInt(KEY_DECIMALS, decimalPlaces)
                .putBoolean(KEY_INPUT_IS_KG, inputIsKg)
                .apply();
    }

    private double getTotalHours() {
        double sum = earlyHours + midHours + nightHours;
        return sum > 0 ? sum : 24.0;
    }

    private double convertBySg(double primaryVal, double sg) {
        double safeSg = sg > 0 ? sg : 1.0;
        return inputIsKg ? (primaryVal / safeSg) : (primaryVal * safeSg);
    }

    private String primaryUnit() {
        return inputIsKg ? "kg" : "L";
    }

    private String convertedUnit() {
        return inputIsKg ? "L" : "kg";
    }

    private String fmt(double val) {
        if (val == 0.0) return "0";
        String s = String.format(Locale.US, "%." + decimalPlaces + "f", val);
        if (decimalPlaces > 0 && s.contains(".")) {
            s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        }
        return s;
    }

    private String fmtSg(double sg) {
        return String.format(Locale.US, "%.2f", sg);
    }

    private String fmtHours(double h) {
        if (h == Math.floor(h)) {
            return String.format(Locale.US, "%.0fh", h);
        }
        return String.format(Locale.US, "%.1fh", h);
    }

    // ===================== UI Helpers =====================

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private GradientDrawable roundedBg(int fill, int radiusDp, int stroke, int strokeDp) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(fill);
        gd.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) {
            gd.setStroke(dp(strokeDp), stroke);
        }
        return gd;
    }

    private View spacer(int wDp, int hDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(dp(wDp), dp(hDp)));
        return v;
    }

    // ===================== Build Main UI =====================

    private void buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(COL_BG_APP);

        // --- Header ---
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(16), dp(14), dp(16), dp(6));

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        titleCol.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));

        TextView txtTitle = new TextView(this);
        txtTitle.setText("藥液班別用量計算");
        txtTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        txtTitle.setTextColor(COL_TEXT_PRIMARY);
        txtTitle.setTypeface(null, Typeface.BOLD);
        titleCol.addView(txtTitle);

        txtSubtitle = new TextView(this);
        txtSubtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        txtSubtitle.setTextColor(COL_CYAN);
        titleCol.addView(txtSubtitle);
        header.addView(titleCol);

        header.addView(createTopPill("＋ 藥液", v -> showAddChemDialog()));
        header.addView(spacer(6, 1));
        header.addView(createTopPill("📜 歷史", v -> showHistoryDialog()));
        header.addView(spacer(6, 1));
        header.addView(createTopPill("⚙️", v -> showSettingsDialog()));
        root.addView(header);

        // --- Unit & SG Mode Bar ---
        LinearLayout modeBar = new LinearLayout(this);
        modeBar.setOrientation(LinearLayout.HORIZONTAL);
        modeBar.setGravity(Gravity.CENTER_VERTICAL);
        modeBar.setPadding(dp(14), dp(4), dp(14), dp(8));

        btnUnitMode = new TextView(this);
        btnUnitMode.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        btnUnitMode.setTypeface(null, Typeface.BOLD);
        btnUnitMode.setGravity(Gravity.CENTER);
        btnUnitMode.setPadding(dp(12), dp(7), dp(12), dp(7));
        btnUnitMode.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        btnUnitMode.setOnClickListener(v -> {
            inputIsKg = !inputIsKg;
            saveShiftSettings();
            refreshUnitModeBanner();
            refreshAllCards();
        });
        modeBar.addView(btnUnitMode);
        root.addView(modeBar);

        // --- Scrollable Cards List ---
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1.0f));

        layCardsContainer = new LinearLayout(this);
        layCardsContainer.setOrientation(LinearLayout.VERTICAL);
        layCardsContainer.setPadding(dp(14), dp(2), dp(14), dp(16));
        sv.addView(layCardsContainer);
        root.addView(sv);

        // --- Fixed Bottom Action Dock ---
        LinearLayout dock = new LinearLayout(this);
        dock.setOrientation(LinearLayout.HORIZONTAL);
        dock.setBackgroundColor(COL_BG_CARD);
        dock.setPadding(dp(12), dp(10), dp(12), dp(12));
        dock.setElevation(dp(8));

        dock.addView(createDockBtn("📋 複製", COL_BLUE, 1.05f, v -> copyReport()));
        dock.addView(spacer(6, 1));
        dock.addView(createDockBtn("📤 分享", COL_EMERALD, 1.05f, v -> shareReport()));
        dock.addView(spacer(6, 1));
        dock.addView(createDockBtn("💾 存檔", COL_BG_ELEVATED, 0.95f, v -> saveSnapshot()));
        dock.addView(spacer(6, 1));
        dock.addView(createDockBtn("🗑️ 清空", COL_RED, 0.85f, v -> confirmClearAll()));
        root.addView(dock);

        setContentView(root);
        refreshSubtitle();
        refreshUnitModeBanner();
    }

    private TextView createTopPill(String text, View.OnClickListener listener) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tv.setTextColor(COL_TEXT_PRIMARY);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setBackground(roundedBg(COL_BG_CARD, 10, COL_BORDER, 1));
        tv.setPadding(dp(10), dp(7), dp(10), dp(7));
        tv.setOnClickListener(listener);
        return tv;
    }

    private Button createDockBtn(String text, int bgColor, float weight, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(text);
        btn.setAllCaps(false);
        btn.setTextColor(Color.WHITE);
        btn.setTypeface(null, Typeface.BOLD);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setBackground(roundedBg(bgColor, 12, bgColor == COL_BG_ELEVATED ? COL_BORDER : 0, bgColor == COL_BG_ELEVATED ? 1 : 0));
        btn.setLayoutParams(new LinearLayout.LayoutParams(0, dp(48), weight));
        btn.setOnClickListener(listener);
        return btn;
    }

    private void refreshSubtitle() {
        txtSubtitle.setText(
                "早 " + fmtHours(earlyHours)
                        + " · 中 " + fmtHours(midHours)
                        + " · 夜 " + fmtHours(nightHours)
                        + "（共 " + fmtHours(getTotalHours()) + "）"
        );
    }

    private void refreshUnitModeBanner() {
        if (inputIsKg) {
            btnUnitMode.setText("🔄 輸入單位：公斤 (kg) → 自動 ÷ 比重換算公升 (L)  ｜ 點此切換");
            btnUnitMode.setTextColor(COL_EMERALD);
            btnUnitMode.setBackground(roundedBg(COL_EMERALD_TINT, 10, COL_EMERALD, 1));
        } else {
            btnUnitMode.setText("🔄 輸入單位：公升 (L) → 自動 × 比重換算公斤 (kg)  ｜ 點此切換");
            btnUnitMode.setTextColor(COL_CYAN);
            btnUnitMode.setBackground(roundedBg(COL_CYAN_TINT, 10, COL_CYAN, 1));
        }
    }

    private void rebuildCards() {
        layCardsContainer.removeAllViews();
        cardViewsList.clear();

        for (int i = 0; i < items.size(); i++) {
            final int index = i;
            final ChemItem item = items.get(i);
            CardViews cv = new CardViews();

            LinearLayout card = new LinearLayout(this);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackground(roundedBg(COL_BG_CARD, 14, COL_BORDER, 1));
            card.setPadding(dp(14), dp(12), dp(14), dp(12));
            LinearLayout.LayoutParams lpCard = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpCard.bottomMargin = dp(10);
            card.setLayoutParams(lpCard);

            // Row 1: Chemical Name + Editable SG Pill + Hourly Pill + Remove
            LinearLayout topRow = new LinearLayout(this);
            topRow.setOrientation(LinearLayout.HORIZONTAL);
            topRow.setGravity(Gravity.CENTER_VERTICAL);

            TextView txtName = new TextView(this);
            txtName.setText("🧪 " + item.name);
            txtName.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            txtName.setTextColor(COL_TEXT_PRIMARY);
            txtName.setTypeface(null, Typeface.BOLD);
            txtName.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f));
            topRow.addView(txtName);

            // Tappable Specific Gravity (比重) Pill
            cv.txtSgPill = new TextView(this);
            cv.txtSgPill.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            cv.txtSgPill.setTextColor(COL_AMBER);
            cv.txtSgPill.setTypeface(null, Typeface.BOLD);
            cv.txtSgPill.setBackground(roundedBg(COL_AMBER_TINT, 10, COL_AMBER, 1));
            cv.txtSgPill.setPadding(dp(8), dp(4), dp(8), dp(4));
            cv.txtSgPill.setOnClickListener(v -> showEditSgDialog(index));
            topRow.addView(cv.txtSgPill);

            topRow.addView(spacer(6, 1));

            cv.txtHourly = new TextView(this);
            cv.txtHourly.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            cv.txtHourly.setTextColor(COL_CYAN);
            cv.txtHourly.setTypeface(null, Typeface.BOLD);
            cv.txtHourly.setBackground(roundedBg(COL_CYAN_TINT, 10, 0, 0));
            cv.txtHourly.setPadding(dp(8), dp(4), dp(8), dp(4));
            topRow.addView(cv.txtHourly);

            if (items.size() > 1) {
                TextView btnDel = new TextView(this);
                btnDel.setText("✕");
                btnDel.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
                btnDel.setTextColor(COL_TEXT_MUTED);
                btnDel.setPadding(dp(8), dp(2), dp(2), dp(2));
                btnDel.setOnClickListener(v -> confirmRemoveChem(index));
                topRow.addView(btnDel);
            }
            card.addView(topRow);

            // Row 2: Daily Total Input + SG Converted Total
            LinearLayout inputRow = new LinearLayout(this);
            inputRow.setOrientation(LinearLayout.HORIZONTAL);
            inputRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams lpIn = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpIn.topMargin = dp(10);
            inputRow.setLayoutParams(lpIn);

            cv.lblTotalUnit = new TextView(this);
            cv.lblTotalUnit.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            cv.lblTotalUnit.setTextColor(COL_TEXT_MUTED);
            inputRow.addView(cv.lblTotalUnit);

            EditText edt = new EditText(this);
            edt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
            edt.setHint("輸入日總量");
            edt.setHintTextColor(Color.parseColor("#475569"));
            edt.setTextColor(COL_TEXT_PRIMARY);
            edt.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
            edt.setTypeface(null, Typeface.BOLD);
            edt.setSingleLine(true);
            edt.setText(item.rawTotal);
            edt.setBackground(roundedBg(COL_BG_INPUT, 10, COL_BORDER, 1));
            edt.setPadding(dp(12), dp(8), dp(12), dp(8));
            LinearLayout.LayoutParams lpEdt = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
            lpEdt.leftMargin = dp(6);
            edt.setLayoutParams(lpEdt);
            cv.edtTotal = edt;
            inputRow.addView(edt);

            cv.txtConvertedTotal = new TextView(this);
            cv.txtConvertedTotal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            cv.txtConvertedTotal.setTextColor(COL_EMERALD);
            cv.txtConvertedTotal.setTypeface(null, Typeface.BOLD);
            cv.txtConvertedTotal.setPadding(dp(8), 0, 0, 0);
            inputRow.addView(cv.txtConvertedTotal);

            card.addView(inputRow);

            // Row 3: Three Shifts Breakdown Boxes (早 / 中 / 夜)
            LinearLayout shiftRow = new LinearLayout(this);
            shiftRow.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams lpShift = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lpShift.topMargin = dp(10);
            shiftRow.setLayoutParams(lpShift);

            TextView[] earlyBox = createShiftCell(shiftRow, "☀️ 早班 " + fmtHours(earlyHours), COL_AMBER, 0, 4);
            TextView[] midBox = createShiftCell(shiftRow, "🌇 中班 " + fmtHours(midHours), COL_CYAN, 4, 4);
            TextView[] nightBox = createShiftCell(shiftRow, "🌙 夜班 " + fmtHours(nightHours), COL_INDIGO, 4, 0);

            cv.txtEarlyTitle = earlyBox[0];
            cv.txtEarlyVal = earlyBox[1];
            cv.txtEarlySub = earlyBox[2];
            cv.txtMidTitle = midBox[0];
            cv.txtMidVal = midBox[1];
            cv.txtMidSub = midBox[2];
            cv.txtNightTitle = nightBox[0];
            cv.txtNightVal = nightBox[1];
            cv.txtNightSub = nightBox[2];

            card.addView(shiftRow);

            edt.addTextChangedListener(new TextWatcher() {
                @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    item.rawTotal = s.toString().trim();
                    updateCardNumbers(item, cv);
                    saveItems();
                }
            });

            updateCardNumbers(item, cv);
            cardViewsList.add(cv);
            layCardsContainer.addView(card);
        }
    }

    private TextView[] createShiftCell(LinearLayout parent, String title, int accentColor, int leftMarginDp, int rightMarginDp) {
        LinearLayout cell = new LinearLayout(this);
        cell.setOrientation(LinearLayout.VERTICAL);
        cell.setGravity(Gravity.CENTER);
        cell.setBackground(roundedBg(COL_BG_INPUT, 10, COL_BORDER, 1));
        cell.setPadding(dp(4), dp(8), dp(4), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.0f);
        lp.leftMargin = dp(leftMarginDp);
        lp.rightMargin = dp(rightMarginDp);
        cell.setLayoutParams(lp);

        TextView tvTitle = new TextView(this);
        tvTitle.setText(title);
        tvTitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvTitle.setTextColor(accentColor);
        tvTitle.setTypeface(null, Typeface.BOLD);
        cell.addView(tvTitle);

        TextView tvVal = new TextView(this);
        tvVal.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        tvVal.setTextColor(COL_TEXT_PRIMARY);
        tvVal.setTypeface(null, Typeface.BOLD);
        tvVal.setPadding(0, dp(4), 0, 0);
        cell.addView(tvVal);

        TextView tvSub = new TextView(this);
        tvSub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        tvSub.setTextColor(COL_TEXT_MUTED);
        tvSub.setPadding(0, dp(2), 0, 0);
        cell.addView(tvSub);

        parent.addView(cell);
        return new TextView[]{tvTitle, tvVal, tvSub};
    }

    private void updateCardNumbers(ChemItem item, CardViews cv) {
        double total = item.getTotalValue();
        double totalH = getTotalHours();
        double hourly = total / totalH;

        String pUnit = primaryUnit();
        String cUnit = convertedUnit();

        cv.txtSgPill.setText("⚖️ 比重 " + fmtSg(item.sg));
        cv.txtHourly.setText("時均 " + fmt(hourly) + " " + pUnit);
        cv.lblTotalUnit.setText("日總量 (" + pUnit + ")：");
        cv.txtConvertedTotal.setText("≈ " + fmt(convertBySg(total, item.sg)) + " " + cUnit);

        double early = hourly * earlyHours;
        double mid = hourly * midHours;
        double night = hourly * nightHours;

        cv.txtEarlyVal.setText(fmt(early) + " " + pUnit);
        cv.txtEarlySub.setText("≈ " + fmt(convertBySg(early, item.sg)) + " " + cUnit);

        cv.txtMidVal.setText(fmt(mid) + " " + pUnit);
        cv.txtMidSub.setText("≈ " + fmt(convertBySg(mid, item.sg)) + " " + cUnit);

        cv.txtNightVal.setText(fmt(night) + " " + pUnit);
        cv.txtNightSub.setText("≈ " + fmt(convertBySg(night, item.sg)) + " " + cUnit);
    }

    private void refreshAllCards() {
        refreshSubtitle();
        for (int i = 0; i < items.size() && i < cardViewsList.size(); i++) {
            CardViews cv = cardViewsList.get(i);
            cv.txtEarlyTitle.setText("☀️ 早班 " + fmtHours(earlyHours));
            cv.txtMidTitle.setText("🌇 中班 " + fmtHours(midHours));
            cv.txtNightTitle.setText("🌙 夜班 " + fmtHours(nightHours));
            updateCardNumbers(items.get(i), cv);
        }
    }

    // ===================== Report Export / History =====================

    private String buildReportText() {
        String dateStr = new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.getDefault()).format(new Date());
        double totalH = getTotalHours();
        String pUnit = primaryUnit();
        String cUnit = convertedUnit();

        StringBuilder sb = new StringBuilder();
        sb.append("🧪 藥液班別用量表（").append(dateStr).append("）\n");
        sb.append("班別時數：早 ").append(fmtHours(earlyHours))
                .append(" / 中 ").append(fmtHours(midHours))
                .append(" / 夜 ").append(fmtHours(nightHours)).append("\n");
        sb.append("────────────────────\n");

        for (ChemItem item : items) {
            double total = item.getTotalValue();
            double hourly = total / totalH;
            double early = hourly * earlyHours;
            double mid = hourly * midHours;
            double night = hourly * nightHours;

            sb.append("【").append(item.name).append("｜比重 ").append(fmtSg(item.sg)).append("】\n")
                    .append("  日總量：").append(fmt(total)).append(" ").append(pUnit)
                    .append("（≈ ").append(fmt(convertBySg(total, item.sg))).append(" ").append(cUnit)
                    .append("｜時均 ").append(fmt(hourly)).append(" ").append(pUnit).append("/h）\n")
                    .append("  早班：").append(fmt(early)).append(" ").append(pUnit)
                    .append("（").append(fmt(convertBySg(early, item.sg))).append(" ").append(cUnit).append("）\n")
                    .append("  中班：").append(fmt(mid)).append(" ").append(pUnit)
                    .append("（").append(fmt(convertBySg(mid, item.sg))).append(" ").append(cUnit).append("）\n")
                    .append("  夜班：").append(fmt(night)).append(" ").append(pUnit)
                    .append("（").append(fmt(convertBySg(night, item.sg))).append(" ").append(cUnit).append("）\n");
        }
        return sb.toString().trim();
    }

    private void copyReport() {
        String report = buildReportText();
        ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("藥液班別用量表", report));
            Toast.makeText(this, "✅ 已複製報表（含比重換算），可直接貼到 LINE！", Toast.LENGTH_SHORT).show();
        }
    }

    private void shareReport() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "藥液班別用量表");
        intent.putExtra(Intent.EXTRA_TEXT, buildReportText());
        startActivity(Intent.createChooser(intent, "分享藥液班別用量表…"));
    }

    private void saveSnapshot() {
        try {
            JSONArray hist = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            JSONObject entry = new JSONObject();
            entry.put("time", new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(new Date()));
            entry.put("text", buildReportText());

            JSONArray updated = new JSONArray();
            updated.put(entry);
            for (int i = 0; i < Math.min(hist.length(), 49); i++) {
                updated.put(hist.get(i));
            }
            prefs.edit().putString(KEY_HISTORY, updated.toString()).apply();
            Toast.makeText(this, "💾 已儲存至歷史紀錄！", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "❌ 存檔失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }

    // ===================== Dialogs =====================

    private void showEditSgDialog(int idx) {
        ChemItem item = items.get(idx);
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(20), dp(12), dp(20), dp(8));

        TextView hint = new TextView(this);
        hint.setText("設定「" + item.name + "」的比重（Specific Gravity）：\n例如：水=1.00、硫酸=1.84、液鹼=1.48");
        hint.setTextColor(COL_TEXT_MUTED);
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        hint.setPadding(0, 0, 0, dp(8));
        lay.addView(hint);

        EditText edtSg = new EditText(this);
        edtSg.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edtSg.setText(fmtSg(item.sg));
        edtSg.setTextColor(COL_TEXT_PRIMARY);
        edtSg.setSelectAllOnFocus(true);
        lay.addView(edtSg);

        new AlertDialog.Builder(this)
                .setTitle("⚖️ 設定藥水比重")
                .setView(lay)
                .setPositiveButton("套用", (d, w) -> {
                    try {
                        double val = Double.parseDouble(edtSg.getText().toString().trim());
                        if (val > 0 && val <= 20.0) {
                            item.sg = val;
                            saveItems();
                            refreshAllCards();
                            Toast.makeText(this, "✅ 「" + item.name + "」比重已設為 " + fmtSg(val), Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(this, "⚠️ 請輸入大於 0 的正確比重數值", Toast.LENGTH_SHORT).show();
                        }
                    } catch (NumberFormatException e) {
                        Toast.makeText(this, "⚠️ 比重格式不正確", Toast.LENGTH_SHORT).show();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmClearAll() {
        new AlertDialog.Builder(this)
                .setTitle("🗑️ 清空全部數值？")
                .setMessage("將清除所有藥液的日總量數值（藥液項目、比重設定與歷史存檔會保留）。")
                .setPositiveButton("確定清空", (d, w) -> {
                    for (int i = 0; i < items.size(); i++) {
                        items.get(i).rawTotal = "";
                        if (i < cardViewsList.size()) {
                            cardViewsList.get(i).edtTotal.setText("");
                        }
                    }
                    saveItems();
                    Toast.makeText(this, "✅ 已清空全部數值", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void confirmRemoveChem(int idx) {
        String name = items.get(idx).name;
        new AlertDialog.Builder(this)
                .setTitle("移除藥液項目")
                .setMessage("確定要移除「" + name + "」嗎？")
                .setPositiveButton("移除", (d, w) -> {
                    items.remove(idx);
                    saveItems();
                    rebuildCards();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showAddChemDialog() {
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(20), dp(12), dp(20), dp(8));

        TextView lblName = new TextView(this);
        lblName.setText("藥液名稱：");
        lblName.setTextColor(COL_CYAN);
        lblName.setTypeface(null, Typeface.BOLD);
        lay.addView(lblName);

        EditText edtName = new EditText(this);
        edtName.setHint("例如：氯化銅、雙氧水");
        edtName.setTextColor(COL_TEXT_PRIMARY);
        edtName.setHintTextColor(COL_TEXT_MUTED);
        lay.addView(edtName);

        EditText edtSg = addLabeledNumberField(lay, "⚖️ 比重（預設 1.00）：", "1.00");

        new AlertDialog.Builder(this)
                .setTitle("🧪 新增藥液項目")
                .setView(lay)
                .setPositiveButton("新增", (d, w) -> {
                    String name = edtName.getText().toString().trim();
                    if (!name.isEmpty()) {
                        double sg = guessDefaultSg(name);
                        try {
                            double parsed = Double.parseDouble(edtSg.getText().toString().trim());
                            if (parsed > 0 && parsed <= 20.0) sg = parsed;
                        } catch (NumberFormatException ignored) {
                        }
                        items.add(new ChemItem(name, "", sg));
                        saveItems();
                        rebuildCards();
                    }
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private void showSettingsDialog() {
        ScrollView sv = new ScrollView(this);
        LinearLayout lay = new LinearLayout(this);
        lay.setOrientation(LinearLayout.VERTICAL);
        lay.setPadding(dp(20), dp(16), dp(20), dp(12));

        EditText edtEarly = addLabeledNumberField(lay, "☀️ 早班時數（小時）：", String.valueOf(earlyHours));
        EditText edtMid = addLabeledNumberField(lay, "🌇 中班時數（小時）：", String.valueOf(midHours));
        EditText edtNight = addLabeledNumberField(lay, "🌙 夜班時數（小時）：", String.valueOf(nightHours));

        TextView lblDec = new TextView(this);
        lblDec.setText("顯示小數位數：");
        lblDec.setTextColor(COL_CYAN);
        lblDec.setTypeface(null, Typeface.BOLD);
        lblDec.setPadding(0, dp(12), 0, dp(4));
        lay.addView(lblDec);

        Spinner spinDec = new Spinner(this);
        String[] decLabels = {"整數（0 位小數）", "小數 1 位", "小數 2 位（預設）", "小數 3 位"};
        ArrayAdapter<String> adp = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, decLabels);
        spinDec.setAdapter(adp);
        spinDec.setSelection(Math.max(0, Math.min(3, decimalPlaces)));
        lay.addView(spinDec);

        sv.addView(lay);

        new AlertDialog.Builder(this)
                .setTitle("⚙️ 班別時數與顯示設定")
                .setView(sv)
                .setPositiveButton("儲存", (d, w) -> {
                    try {
                        double e = Double.parseDouble(edtEarly.getText().toString().trim());
                        double m = Double.parseDouble(edtMid.getText().toString().trim());
                        double n = Double.parseDouble(edtNight.getText().toString().trim());
                        if (e > 0 && m > 0 && n > 0) {
                            earlyHours = e;
                            midHours = m;
                            nightHours = n;
                        }
                    } catch (NumberFormatException ignored) {
                    }
                    decimalPlaces = spinDec.getSelectedItemPosition();
                    saveShiftSettings();
                    refreshAllCards();
                    Toast.makeText(this, "✅ 設定已更新", Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton("恢復預設 5 藥液", (d, w) -> {
                    earlyHours = 8.5;
                    midHours = 8.0;
                    nightHours = 7.5;
                    decimalPlaces = 2;
                    inputIsKg = false;
                    saveShiftSettings();
                    loadDefaultItems();
                    refreshSubtitle();
                    refreshUnitModeBanner();
                    rebuildCards();
                    Toast.makeText(this, "✅ 已恢復預設藥液、比重與 8.5 / 8 / 7.5 時數", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("取消", null)
                .show();
    }

    private EditText addLabeledNumberField(LinearLayout parent, String label, String value) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextColor(COL_CYAN);
        tv.setTypeface(null, Typeface.BOLD);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        tv.setPadding(0, dp(8), 0, dp(2));
        parent.addView(tv);

        EditText edt = new EditText(this);
        edt.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        edt.setText(value);
        edt.setTextColor(COL_TEXT_PRIMARY);
        parent.addView(edt);
        return edt;
    }

    private void showHistoryDialog() {
        try {
            JSONArray hist = new JSONArray(prefs.getString(KEY_HISTORY, "[]"));
            if (hist.length() == 0) {
                new AlertDialog.Builder(this)
                        .setTitle("📜 歷史紀錄")
                        .setMessage("目前尚無存檔紀錄。點擊底部「💾 存檔」即可保存當日計算結果。")
                        .setPositiveButton("關閉", null)
                        .show();
                return;
            }

            ScrollView sv = new ScrollView(this);
            LinearLayout lay = new LinearLayout(this);
            lay.setOrientation(LinearLayout.VERTICAL);
            lay.setPadding(dp(14), dp(10), dp(14), dp(10));

            for (int i = 0; i < hist.length(); i++) {
                JSONObject obj = hist.getJSONObject(i);
                String text = obj.optString("text", "");

                LinearLayout box = new LinearLayout(this);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setBackground(roundedBg(COL_BG_APP, 10, COL_BORDER, 1));
                box.setPadding(dp(12), dp(10), dp(12), dp(10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.bottomMargin = dp(10);
                box.setLayoutParams(lp);

                TextView tv = new TextView(this);
                tv.setText(text);
                tv.setTextColor(COL_TEXT_PRIMARY);
                tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                box.addView(tv);

                TextView btnCopy = new TextView(this);
                btnCopy.setText("📋 複製此紀錄");
                btnCopy.setTextColor(COL_CYAN);
                btnCopy.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
                btnCopy.setTypeface(null, Typeface.BOLD);
                btnCopy.setPadding(0, dp(8), 0, 0);
                btnCopy.setOnClickListener(v -> {
                    ClipboardManager cm = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                    if (cm != null) {
                        cm.setPrimaryClip(ClipData.newPlainText("藥液班別用量表", text));
                        Toast.makeText(this, "✅ 已複製該筆歷史紀錄", Toast.LENGTH_SHORT).show();
                    }
                });
                box.addView(btnCopy);
                lay.addView(box);
            }

            sv.addView(lay);
            new AlertDialog.Builder(this)
                    .setTitle("📜 歷史紀錄（共 " + hist.length() + " 筆）")
                    .setView(sv)
                    .setPositiveButton("關閉", null)
                    .setNegativeButton("清空歷史", (d, w) -> {
                        prefs.edit().remove(KEY_HISTORY).apply();
                        Toast.makeText(this, "🗑️ 歷史紀錄已清空", Toast.LENGTH_SHORT).show();
                    })
                    .show();
        } catch (Exception e) {
            Toast.makeText(this, "讀取歷史失敗: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
}
