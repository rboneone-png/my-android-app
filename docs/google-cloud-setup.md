# Google 雲端同步設定教學

App 要能把資料備份到你的 Google Drive，需要先在 Google Cloud 建立一個（免費的）設定，讓 Google 認得這個 App。只需要做一次，大約 10 分鐘。

> 這些設定只是「讓 Google 認得這個 App」，不會產生任何費用。

## 會用到的資料

| 項目 | 值 |
|---|---|
| 套件名稱（Package name） | `com.rboneone.stockrecord` |
| SHA-1 憑證指紋 | `4B:C2:51:67:E4:BC:1E:A8:7B:54:2F:8C:BB:9E:42:A2:1D:1F:8B:4D` |
| 權限範圍（Scope） | `https://www.googleapis.com/auth/drive.appdata` |

## 步驟 1：建立專案

1. 打開 https://console.cloud.google.com/ ，用你的 Google 帳號登入
2. 左上角專案選單 → **新增專案**
3. 專案名稱填 `StockRecord`，按 **建立**
4. 建立好後，確認左上角已切換到這個專案

## 步驟 2：啟用 Google Drive API

1. 打開 https://console.cloud.google.com/apis/library/drive.googleapis.com
2. 按 **啟用**

## 步驟 3：設定 OAuth 同意畫面

1. 打開 https://console.cloud.google.com/auth/overview ，按 **開始**
2. **應用程式資訊**：名稱填 `股票記帳`，使用者支援電子郵件選你的 Gmail
3. **目標對象**：選 **外部**
4. **聯絡資訊**：填你的 Gmail
5. 勾選同意政策，按 **建立**

## 步驟 4：建立 Android 用戶端

1. 打開 https://console.cloud.google.com/auth/clients ，按 **建立用戶端**
2. **應用程式類型** 選 **Android**
3. **套件名稱** 填 `com.rboneone.stockrecord`
4. **SHA-1 憑證指紋** 填 `4B:C2:51:67:E4:BC:1E:A8:7B:54:2F:8C:BB:9E:42:A2:1D:1F:8B:4D`
5. 按 **建立**

## 步驟 5：加入權限範圍

1. 打開 https://console.cloud.google.com/auth/scopes ，按 **新增或移除範圍**
2. 在清單中勾選 `.../auth/drive.appdata`（或在手動新增欄位貼上 `https://www.googleapis.com/auth/drive.appdata`）
3. 按 **更新**，再按 **儲存**

## 步驟 6：開放使用者

到 https://console.cloud.google.com/auth/audience ，二擇一：

- **只有自己和家人用**：在 **測試使用者** 加入每個要使用同步功能的 Gmail。
  注意：「測試中」狀態下，Google 的授權大約每 7 天會過期，屆時自動備份會失敗，需要到 App 設定頁重新按一次「連結 Google 帳號」。
- **不想每 7 天重新連結**：按 **發布應用程式**，改成「實際運作中」。這個 App 只使用 `drive.appdata`（App 專用資料夾）這種非敏感權限，一般不需要經過 Google 審查。

## 步驟 7：在 App 裡連結

1. 打開 App → **設定** → **Google 雲端同步** → **連結 Google 帳號**
2. 選擇 Google 帳號並同意授權
3. 選擇 **手動** 或 **自動**（自動可選 1、6、12、24 小時備份一次）
4. 按 **立即備份** 測試看看

## 常見問題

- **按連結後出現錯誤（例如 DEVELOPER_ERROR 或錯誤代碼 10）**：通常是步驟 4 的套件名稱或 SHA-1 填錯，請逐字核對。
- **出現「這個應用程式未經 Google 驗證」**：按 **進階** → **前往股票記帳**。
- **換新手機**：安裝 App → 新增**同名**帳號 → 連結同一個 Google 帳號 → 按 **從雲端還原**。
  雲端備份是依「帳號名稱」分開存放的，所以名稱必須一模一樣。
