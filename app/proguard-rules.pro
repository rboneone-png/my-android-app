# 正式版 R8 規則
#
# App 本身的類別全部保留：這個 App 沒有在實機上做過混淆後的完整測試，
# 保留自己的程式碼可避免反射（Room、WorkManager、enum 名稱）在正式版出錯；
# 第三方函式庫仍會被縮減與最佳化。
-keep class com.rboneone.stockrecord.** { *; }

# 備份檔與資料庫以 enum 名稱儲存（例如 Market.TW），名稱不可被改動
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留行號，閃退時的錯誤紀錄比較容易追查
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 指紋／臉部辨識的畫面（Fragment）可能在旋轉螢幕等情況下依類別名稱重建，整個函式庫保留
-keep class androidx.biometric.** { *; }
