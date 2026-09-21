# Android checkpoint: AI, OCR, phân bổ trực tiếp và No image

Ngày kiểm tra: 17/09/2026. Branch: `fix/android-profile-search-finance`. HEAD giữ nguyên `04b3bf3`. Không commit/push/merge/rebase/reset/stash; staging giữ trống.

## 1. UI Trợ lý AI trước/sau

Trước: sheet có tiêu đề `Trợ lý giao dịch`, ô nhập toàn chiều ngang, hai thao tác Tạo bản nháp/Micro; OCR nằm trong Thêm giao dịch.

Sau: sheet trắng, bo góc trên 24dp, drag handle, tiêu đề `✨ Trợ lý Tài chính WalletWise`. Hội thoại và draft cuộn trong phần trên; composer cố định dưới cùng, dùng hồng WalletWise `#FA3B70`. Camera → thư viện → micro nằm bên trái; input bo tròn viền hồng bên phải, placeholder `Nhập nội dung`, nút gửi trong input. Mỗi nút 48dp; có ripple, disabled và trạng thái xử lý. Cụm thao tác lấy khoảng 44% chiều ngang; ở màn nhỏ, nút gửi chuyển xuống dòng để giữ đủ chỗ cho placeholder.

Sheet áp dụng IME/insets; đã mở bàn phím hệ thống và thấy composer nằm trên bàn phím. Có test kích thước 320dp, thứ tự và callback của ba nút. Draft cuộn được đến ngày, thanh toán và xác nhận.

Ảnh runtime:

- [Trước](../.artifacts/android-ui-allocation-revision/ai-before.png)
- [Sau](../.artifacts/android-ui-allocation-revision/ai-after.png)
- [Bàn phím mở](../.artifacts/android-ui-allocation-revision/ai-keyboard.png)
- [Draft hóa đơn trong AI](../.artifacts/android-ui-allocation-revision/ai-receipt-synthetic-result.png)
- [Các field và xác nhận](../.artifacts/android-ui-allocation-revision/ai-receipt-synthetic-edit-fields.png)

## 2. Camera/thư viện/mic trong AI

Camera dùng `TakePicture`, thư viện dùng `PickVisualMedia(ImageOnly)`, micro giữ permission + speech flow + UID guard hiện có. Camera/picker trở lại cùng sheet. Decode/EXIF và ML Kit chạy cục bộ; hiển thị thumbnail và `Đang nhận diện hóa đơn…`. Kết quả chuyển từ `ReceiptTransactionDraft` vào `TransactionDraftPresenter`, không gọi write ở bước nhận diện. Camera không mở được, cancel, OCR rỗng và permission denial có thông báo/fallback, không crash.

Đã mở camera hệ thống, chụp và nhận ảnh emulator. Ảnh cảnh giả lập không có chữ tạo draft cần bổ sung, không tự lưu. Photo picker hệ thống đã chọn fixture `QUAN AN TEST`, đọc đúng **50.000đ, 17/09/2026, Ăn uống, Tiền mặt** và nội dung cửa hàng. Có thể sửa số tiền, loại, danh mục, nội dung, ngày và thanh toán trước xác nhận.

Test UI thực dùng ML Kit + Firebase emulator xác minh camera/picker chỉ tạo draft, không upload ảnh; double confirm chỉ tạo **một document**, qua write use case hiện có. ID draft ổn định và guard đang lưu vẫn được giữ. Text → draft và voice → text → draft không bị bỏ.

Mic denial/cancel và callback speech đã được kiểm tra tự động. Nút mic thật đã mở giao diện Google nhận giọng nói, nhưng dịch vụ emulator báo `Voice search isn't available`: [ảnh](../.artifacts/android-ui-allocation-revision/ai-system-microphone.png). Chưa có transcript thật; không coi callback giả lập là bằng chứng nhận diện giọng nói thành công.

## 3. OCR AI và ảnh đính kèm Thêm giao dịch

Thêm giao dịch đã bỏ receipt OCR, parser và auto-fill. Hai nút camera/thư viện chỉ cập nhật `TransactionAttachmentState`; preview ở đầu form có thể đổi/xóa. File camera thuộc cache riêng, chỉ xóa file do app sở hữu. Picker không xóa ảnh của người dùng. Attachment state được save/restore khi đổi cấu hình.

Đã chạy camera và picker thật trong Thêm giao dịch; chọn cùng fixture hóa đơn vẫn để ô số tiền trống, không tạo AI draft. Đổi và xóa ảnh hoạt động. Lưu không ảnh vẫn dùng write pipeline hiện có. Upload được thực hiện trước write; test uploader trả failure xác minh không ghi document, không navigate và không báo thành công; retry thành công ghi một document. Không thay schema `imageUrl` và không upload hóa đơn OCR.

- [Camera attachment](../.artifacts/android-ui-allocation-revision/add-camera-attachment-preview.png)
- [Picker attachment; số tiền vẫn trống](../.artifacts/android-ui-allocation-revision/add-gallery-attachment-preview.png)
- [Xóa ảnh](../.artifacts/android-ui-allocation-revision/add-attachment-removed.png)

## 4. Nguồn top tám danh mục

`viewModel.categories` lấy từ category session/repository hiện có; không mở listener mới. Helper `transactionCategoryChoices` lọc loại Thu/Chi, bỏ ID trùng, dùng `sortedForDisplay()` giống Cài đặt danh mục rồi `take(8)`. Không hard-code tám tên và không sửa dữ liệu cài đặt. Có ít hơn tám thì hiển thị số hiện có.

Selection của giao dịch sửa nằm ngoài top tám được giữ bằng chip `Đang chọn: …`; fallback giữ tên/ID gốc khi category không còn trong danh sách. Test UI seed category repository đầy đủ, thêm category ngoài top tám rồi sửa/lưu, xác minh giữ nguyên cả tên và ID. Category đã bị nguồn session loại bỏ không trở lại trong top tám; fallback chỉ bảo vệ selection đang sửa.

## 5. Root cause thẻ kế hoạch không giảm

Phát hiện race trong `TransactionRepositoryImpl.observeTransactions`: snapshot primary rỗng khởi chạy đọc legacy. Khi snapshot primary mới đến, coroutine legacy cũ bị hủy. `runCatching { loadLegacyTransactions(...) }.getOrDefault(emptyList())` lại nuốt `CancellationException` và có thể phát **danh sách rỗng cũ sau snapshot mới**. `TransactionSessionController.transactions` bị ghi đè, presenter nhận danh sách rỗng nên `Đã chi` thành 0.

Đã sửa bằng generation tăng ở mỗi callback, hủy job legacy trước xử lý callback mới, rethrow cancellation, `ensureActive()` và kiểm tra generation + UID trước publish. `awaitClose` cũng vô hiệu generation. Không thêm transaction listener. Test deterministic giữ legacy đọc chậm trong `NonCancellable`, cho primary mới phát trước, thả legacy sau và xác minh session vẫn giữ giao dịch primary.

Ngoài ra, dữ liệu legacy có thể lưu **category ID trong field `category`**, trong khi mapping cũ chỉ so tên; đã resolve cả stable `categoryId` lẫn ID legacy qua category session. Metrics tổng cũ dùng bộ tính riêng đã được chuyển sang cùng kết quả live, tránh lọc UID/mapping khác nhau giữa tổng và card.

Lúc bắt đầu emulator đang chạy APK checkpoint cũ và emulator backend chưa chạy. Đã cài APK mới cấu hình `demo-walletwise` và mở Auth/Firestore emulator. Không truy cập hoặc thay Firebase production config. Không có snapshot dữ liệu gốc của giao dịch người dùng để khẳng định nó đi qua nhánh nào; race và lỗi ID legacy nói trên được tái hiện/kiểm chứng bằng fixture và regression tests.

## 6. Nguồn tính toán, công thức và mapping

Chuỗi đã kiểm tra:

```text
Firebase snapshot → TransactionRepositoryImpl
→ TransactionSessionController.transactions (một listener)
→ UID + local tháng kế hoạch + Chi + amount hợp lệ
→ distinct transaction ID (sau lọc)
→ category session resolve ID / legacy ID / tên
→ mapping ID ưu tiên, tên legacy normalize/alias fallback
→ LiveFinancialCalculator
→ SmartBudgetPresenter combine(plan, transactions, mappings, categories)
→ state → card + BudgetUsageTube
```

`categoryId` thêm ở cuối model với default rỗng; wire mapper đọc optional và chỉ ghi khi có ID. Dữ liệu cũ không bị rewrite. Mapping override lưu key `id:<categoryId>` qua repository hiện có; tên legacy trim, case-insensitive, bỏ dấu tiếng Việt. Đổi tên category không làm mất override ID. Aliases mặc định bao gồm ăn uống/nhà cửa/hóa đơn/điện-nước-internet/y tế/di chuyển/sinh hoạt bắt buộc; giải trí/du lịch/mua sắm không thiết yếu; tiết kiệm/đầu tư/mục tiêu tài chính.

Category không xác định vào `Chưa phân nhóm`. `Mua sắm` chung chưa đủ xác định nhu cầu được để chưa phân nhóm. Người dùng chọn bucket trong mapping dialog, tính lại ngay, persist; lỗi write rollback và báo lỗi. Khi mapping chưa tải hoặc lỗi, không âm thầm trừ bucket. Mapping tách theo phương pháp hiện hữu; logout/thay UID xóa state cũ.

```text
allocated = monthlyIncome × allocationPercent / 100
spent = tổng Chi đúng UID/tháng và bucket
remaining = allocated − spent
usedPercent = spent / allocated × 100
remainingBucketPercent = remaining / allocated × 100
remainingIncomePercent = remaining / monthlyIncome × 100
```

Remaining không clamp về 0; chỉ fraction vẽ thanh clamp 0..1. Không lưu/trừ số dư thủ công. Add/edit amount/date/type/category/delete, mapping change, month change, recurring ID/snapshot lặp và logout đều dẫn xuất lại từ cùng flow.

## 7. Bằng chứng add/edit/delete cập nhật thật

Instrumentation dùng **Firebase Auth/Firestore emulator thật**, plan đọc lại từ repository và cùng transaction session, counting wrapper xác minh **listeners=1**. Plan thu nhập 10.000.000đ, ngày theo tháng local hiện tại, UID cùng user. Hai phương pháp đã add/edit/delete, assert presenter và text UI, lưu screenshot:

| Phương pháp | Thao tác | Đã chi thiết yếu | Còn lại thiết yếu |
|---|---|---:|---:|
| 50/30/20 | Add Ăn uống 50.000đ | 50.000đ | **4.950.000đ** |
| 50/30/20 | Edit 100.000đ | 100.000đ | **4.900.000đ** |
| 50/30/20 | Delete | 0đ | **5.000.000đ** |
| 6 chiếc lọ | Add Ăn uống 50.000đ | 50.000đ | **5.450.000đ** |
| 6 chiếc lọ | Edit 100.000đ | 100.000đ | **5.400.000đ** |
| 6 chiếc lọ | Delete | 0đ | **5.500.000đ** |

JARS 50.000đ hiển thị 0,91% dùng quỹ, 99,09% còn lại quỹ, 54,5% còn lại so tổng thu nhập. 50/30/20 hiển thị 1%, 99%, 49,5%. Test còn xác minh legacy category ID Nhà cửa, thay mapping bằng ID, rename category qua repository, repeated snapshot/deterministic ID không tính hai lần và logout.

- [50/30/20 add](../.artifacts/android-ui-allocation-revision/proof/50_30_20-add-50000.png), [edit](../.artifacts/android-ui-allocation-revision/proof/50_30_20-edit-100000.png), [delete](../.artifacts/android-ui-allocation-revision/proof/50_30_20-delete.png)
- [JARS add](../.artifacts/android-ui-allocation-revision/proof/JARS-add-50000.png), [edit](../.artifacts/android-ui-allocation-revision/proof/JARS-edit-100000.png), [delete](../.artifacts/android-ui-allocation-revision/proof/JARS-delete.png)
- [UI ứng dụng chính](../.artifacts/android-ui-allocation-revision/finance-live-walletwise.png)
- [Trace listener, source và số tiền](../.artifacts/android-ui-allocation-revision/runtime-sanitized.log)

## 8. Biểu đồ ống vàng–trắng

Capsule cao 16dp, bo tròn, viền nhẹ, white track, vàng `#FFCE45`; animation 240ms theo Compose motion duration scale. Semantics có progress range, phần trăm thật và trạng thái; số >100% không bị mất trong label. Card giữ tên/tỷ lệ, cấp/chi/còn lại, ba tỷ lệ và trạng thái. Dưới 80% bình thường; 80–<100% cảnh báo; từ 100% thanh vàng đầy và text vượt ngân sách.

Yêu cầu B5 có hai quy ước màu trái nhau. Đã dùng **vàng = đã dùng, trắng = còn lại**, theo yêu cầu phần fill vàng và thanh đầy vàng ở 100%; có legend trực tiếp để tránh hiểu ngược.

- [0%](../.artifacts/android-ui-allocation-revision/proof/JARS-delete.png)
- [50%](../.artifacts/android-ui-allocation-revision/proof/JARS-50percent.png)
- [100%](../.artifacts/android-ui-allocation-revision/proof/JARS-100percent.png)
- [109,09%, còn -500.000đ](../.artifacts/android-ui-allocation-revision/proof/JARS-over-budget.png)

Có ảnh runtime, chưa quay clip. Đã sửa thêm formatter số âm tránh lỗi `-.500.000` thành `-500.000` và thêm regression test.

## 9. Placeholder No image

`NoTransactionImage` dùng Compose vector `ImageNotSupported`, chữ `No image`, màu theme và bo góc 12dp; semantics `Giao dịch không có ảnh`. `TransactionPhoto` trim URL, null/empty/whitespace render placeholder trực tiếp, không tạo Coil request; loading/error có fallback cùng frame. Success vẫn crop ảnh thật. Áp dụng calendar thumbnail ngày có giao dịch, card/grid và detail chung. Ngày không có giao dịch không bị thêm placeholder. Home dùng chung component nên cũng có fallback với layout hiện hữu.

Test cover null/rỗng/whitespace/valid/error thật qua Coil EventListener, zero loader requests cho blank, frame 100dp giữ nguyên, light/dark và accessibility. Runtime phát hiện thumbnail lịch 38dp cắt chữ ở layout ban đầu; đã thêm layout compact icon 10dp, chữ 7sp/line-height 9sp xuống hai dòng, giới hạn width 24dp và bỏ letter spacing để tránh viền bo góc. Test kiểm tra TextLayoutResult không overflow, đủ hai dòng và text có khoảng trống hai mép. Bản compact 8sp đầu tiên bị test bắt overflow vì chữ xuống quá hai dòng; đã giữ log failure và chỉnh typography. Không sửa URL database, không upload hoặc lưu placeholder.

- [Calendar](../.artifacts/android-ui-allocation-revision/history-no-image.png)
- [Transaction card của ngày](../.artifacts/android-ui-allocation-revision/history-day-no-image.png)
- [Detail](../.artifacts/android-ui-allocation-revision/history-detail-no-image.png)

## 10. File tạo/sửa/xóa trong lượt này

Tạo mới:

```text
app/src/main/java/com/example/walletwise/data/image/TransactionAttachmentState.kt
app/src/main/java/com/example/walletwise/presentation/home/TransactionPhoto.kt
shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionCategoryChoices.kt
shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/NoTransactionImage.kt
shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetUsageTube.kt
shared/src/commonTest/kotlin/com/example/walletwise/domain/service/AllocationCheckpointRegressionTest.kt
app/src/androidTest/java/com/example/walletwise/reliability/AssistantActionsRenderTest.kt
app/src/androidTest/java/com/example/walletwise/reliability/TransactionPhotoRenderTest.kt
app/src/androidTest/java/com/example/walletwise/reliability/ReceiptAttachmentFlowRenderTest.kt
app/src/androidTest/java/com/example/walletwise/reliability/AllocationEmulatorUiTest.kt
app/src/androidTest/java/com/example/walletwise/reliability/LegacyFallbackSnapshotEmulatorTest.kt
docs/checkpoint-android-ui-ocr-allocation-placeholder.md
```

Sửa tiếp các file đã tồn tại/đã dirty trước lượt này:

```text
app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt
app/src/main/java/com/example/walletwise/data/draft/AndroidReceiptRecognition.kt
app/src/main/java/com/example/walletwise/presentation/home/AITransactionSheet.kt
app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt
app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt
app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
app/src/main/java/com/example/walletwise/presentation/home/HistoryScreen.kt
shared/src/commonMain/kotlin/com/example/walletwise/domain/model/Transaction.kt
shared/src/commonMain/kotlin/com/example/walletwise/data/mapper/FirestoreWireMapper.kt
shared/src/commonMain/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocation.kt
shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionDraftPresenter.kt
shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt
shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt
shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt
shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/FinancialMappingPresenterTest.kt
shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt
```

Không xóa file source. `.artifacts/android-ui-allocation-revision` và các helper `.artifacts/revision-*.ps1` chứa fixture synthetic, logs, PNG/XML và bản sao APK emulator để review; bị Git ignore. Danh sách Git ở cuối bao gồm cả thay đổi cũ, không được quy hết cho lượt này. `.idea/gradle.xml` dirty từ đầu; hash đối chiếu giữ nguyên. `local.properties` và `app/google-services.json` giữ nguyên hash. Không sửa credential/production config.

## 11. Tests/checks thực tế

Đã chạy mới, tuần tự trên source cuối:

| Lệnh/check | Kết quả | Chi tiết |
|---|---|---|
| `:shared:testAndroidHostTest --rerun-tasks` | PASS | **304 tests**, failure/error/skipped=0 |
| `:app:testDebugUnitTest --rerun-tasks` | PASS | **25 tests**, failure/error/skipped=0 |
| `:shared:compileAndroidMain` | PASS | BUILD SUCCESSFUL |
| `:app:assembleDebug` | PASS | BUILD SUCCESSFUL |
| `:app:lintDebug` | PASS | **0 errors, 55 warnings** |
| Instrumentation 11 lớp được chọn | PASS | **17 tests**, 81,519s trên source cuối; gồm regression thumbnail lịch; Firebase emulator/ML Kit/Compose |
| `git diff --check` | PASS | Exit 0 |

Logs: [shared](../.artifacts/android-ui-allocation-revision/shared-tests.log), [app unit](../.artifacts/android-ui-allocation-revision/app-tests.log), [compile](../.artifacts/android-ui-allocation-revision/shared-compile.log), [assemble](../.artifacts/android-ui-allocation-revision/app-assemble.log), [lint](../.artifacts/android-ui-allocation-revision/app-lint.log), [instrumentation cuối](../.artifacts/android-ui-allocation-revision/final-complete-runtime-tests.log). Image tests riêng trên source cuối: [2 tests PASS / 9,862s](../.artifacts/android-ui-allocation-revision/final-image-tests.log). Lượt trước sửa thumbnail: [16 tests PASS / 81,867s](../.artifacts/android-ui-allocation-revision/race-fixed-runtime-tests.log).

Các lượt đầu có failure: fixture category được thêm trước khi seed danh sách làm test outside-eight sai chuẩn bị; test xác minh tiền âm lộ formatter sai; và test assistant nhiều lần timeout khi session mất snapshot. Đã sửa fixture, formatter và race, rồi chạy lại cả 16 test PASS. Có một lần build runtime trùng build thường làm generated BuildConfig/Kotlin lint lỗi tạm; đã chạy tuần tự lại, build/lint cuối sạch lỗi. Giữ các log failure cũ để audit; không che bằng retry riêng lẻ.

Coverage bắt buộc gồm receipt draft không ghi trước confirm/double submit, action order/callback/disabled/48dp, attachment không OCR và upload failure, top-eight/order/outside selection, mapping ID và legacy accent/case/ID-in-name, Thu/khác tháng/khác UID, edit amount/date/type/category, delete/snapshot/recurring ID, mapping change/unassigned/month/logout, tube 0/50/100/>100 và image blank/error/layout/accessibility.

## 12. Runtime đã/chưa chạy

Đã chạy Android emulator `emulator-5554`, màn 1080×2400, timezone local +07, Auth/Firestore **demo-walletwise**:

- UI AI trắng/hồng, keyboard và scroll draft.
- Camera hệ thống AI chụp/accept ảnh giả lập; OCR rỗng có fallback.
- Photo picker AI chọn hóa đơn synthetic, ML Kit đọc draft đúng.
- Xác nhận/double-confirm ghi đúng một transaction: instrumentation UI dùng callback activity giả lập, ML Kit và Firestore thật; cả 17 test cuối PASS.
- Camera/picker thật ở Add, preview/đổi/xóa, không OCR/auto-fill. Mic thật mở speech activity; dịch vụ emulator không khả dụng.
- Top-eight/outside edit: common tests và instrumentation UI với category repository thật.
- Plan 10 triệu, cả hai phương pháp add/edit/delete: instrumentation repository + live UI thật; ứng dụng chính mở lại fixture JARS 50.000đ.
- Calendar, card và detail No image; logcat/cache lifecycle. Logcat không có FATAL EXCEPTION hoặc token/API key trong phần log được lọc; hai thư mục cache camera receipt/attachment đã trống sau khi đóng luồng. `ADD_TRANSACTION: Write failed (details redacted)` là lỗi uploader được chủ động tiêm trong test failure, không phải một write được báo thành công giả.

Chưa chạy trên thiết bị vật lý và chưa có transcript speech thật. Không dùng camera emulator để chụp hóa đơn giấy thật; hóa đơn rõ được đưa qua picker. Không gọi upload ảnh ra dịch vụ ngoài để kiểm thử (failure/success được tiêm uploader, write dùng Firebase emulator). Không gọi AI provider/cloud có phí. Bộ `ProfileCheckpointEmulatorTest` riêng không rerun toàn bộ gate trong lượt này; Profile/Home Search/financial render regression hiện hữu đã nằm trong nhóm instrumentation được chạy. Không có smoke production.

## 13. Warning/rủi ro còn lại

- Lint 55 warnings, gồm DefaultLocale/ExifInterface/InlinedApi/OldTargetApi, dependency/version, ConfigurationScreenWidthHeight, StaticFieldLeak, resource/KTX/TOML/style; không tuyên bố đã xử lý hết các warning cũ. Báo cáo lint tại `app/build/reports/lint-results-debug.html`.
- AI sheet dùng chiều cao theo cấu hình màn hình nên có cảnh báo ConfigurationScreenWidthHeight; đã kiểm tra IME thật và test composer 320dp, chưa kiểm tra foldable/tablet.
- OCR không chắc chắn cần người dùng kiểm tra field; không tự lưu. Camera emulator chỉ mô phỏng cảnh.
- Credential AI cloud lỗi 401 từ checkpoint trước chưa sửa và chưa gọi lại; text parser local vẫn hoạt động. Khả năng speech phụ thuộc dịch vụ hệ thống.
- Quy ước màu B5 mâu thuẫn được giải quyết bằng legend vàng đã dùng/trắng còn lại như phần 8.
- Emulator và Firebase demo được để lại cho review; fixture không chứa thông tin cá nhân. Không dùng dữ liệu production.

## 14–15. Git status và diff name-status

Output đầy đủ được chèn sau lần kiểm tra cuối bên dưới. Bản trước khi sửa: [status](../.artifacts/android-ui-allocation-revision/git-status-before.txt), [diff](../.artifacts/android-ui-allocation-revision/git-diff-before.txt).

## 16. Đề xuất tách commit và staging

Chỉ đề xuất, chưa commit hoặc stage:

1. **Restore AI assistant UI and receipt actions** — AI sheet/composer, receipt draft handoff, attachment-only Add và category top-eight, tests UI/OCR.
2. **Fix live financial allocation calculation** — chặn stale legacy snapshot, categoryId/wire/mapping, presenter/calculator/card/tube, regression và runtime proof.
3. **Add transaction image fallback** — shared placeholder, Android renderer, calendar/history/detail, image tests.

`categoryId` được receipt/attachment writer dùng chung với allocation, nên khi tách commit cần đặt model/wire ở commit nền phù hợp để từng commit build được. Giữ các thay đổi Profile/Home Search/Money Input đã có. Staging cuối phải rỗng; HEAD/branch và protected hashes được kiểm tra cùng output Git bên dưới.

## Git output cuoi

Branch: `fix/android-profile-search-finance`. HEAD: `04b3bf3`. Staging clean: **True**. `git diff --check`: **PASS**. Protected hashes: **3/3 unchanged**.

### git status --short --untracked-files=all

```text
 M .idea/gradle.xml
 M app/build.gradle.kts
 M app/src/main/AndroidManifest.xml
 M app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt
 M app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
 M app/src/main/java/com/example/walletwise/presentation/home/HistoryScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
 M app/src/main/java/com/example/walletwise/presentation/home/TransactionAIAssistant.kt
 M app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt
 M app/src/main/java/com/example/walletwise/presentation/profile/SmartBudgetPlannerView.kt
 M firebase/checkpoint-04i/firestore.rules
 M gradlew.bat
 M shared/build.gradle.kts
 M shared/src/commonMain/kotlin/com/example/walletwise/data/mapper/FirestoreWireMapper.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/model/Transaction.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalculator.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalendar.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionController.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetSessionController.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfileContents.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfilePresentation.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt
 M shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresentation.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionControllerTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/profile/ProfileUiPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresenterTest.kt
 M shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionSessionControllerTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/AllocationEmulatorUiTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/AssistantActionsRenderTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/AssistantTransactionEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/FinancialRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/HomeSearchRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/LegacyFallbackSnapshotEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/LiveAllocationEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ProfileCheckpointEmulatorTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ProfileRenderRegressionTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ReceiptAttachmentFlowRenderTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/ReceiptOcrRuntimeTest.kt
?? app/src/androidTest/java/com/example/walletwise/reliability/TransactionPhotoRenderTest.kt
?? app/src/main/java/com/example/walletwise/data/draft/AndroidDraftDateTimeProvider.kt
?? app/src/main/java/com/example/walletwise/data/draft/AndroidReceiptRecognition.kt
?? app/src/main/java/com/example/walletwise/data/draft/FormTransactionTime.kt
?? app/src/main/java/com/example/walletwise/data/image/TransactionAttachmentState.kt
?? app/src/main/java/com/example/walletwise/data/repository/FinancialMappingRepositoryImpl.kt
?? app/src/main/java/com/example/walletwise/presentation/home/AITransactionSheet.kt
?? app/src/main/java/com/example/walletwise/presentation/home/TransactionPhoto.kt
?? app/src/test/java/com/example/walletwise/data/draft/FormTransactionTimeTest.kt
?? docs/checkpoint-android-ai-receipt-live-allocation.md
?? docs/checkpoint-android-profile-search-finance.md
?? docs/checkpoint-android-ui-ocr-allocation-placeholder.md
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/model/FinancialMethod.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/model/TransactionDraft.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/repository/FinancialMappingRepository.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocation.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/MoneyInput.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/ReceiptTextParser.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/domain/service/TransactionDraftParser.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetUsageTube.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/GroupedMoneyTransformation.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/NoTransactionImage.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/ReceiptDraftPresenter.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionCategoryChoices.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionDraftPresenter.kt
?? shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/VoiceInputGate.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/AllocationCheckpointRegressionTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/DraftAndMoneyTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/FinancialAllocationMethodsTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/domain/service/LiveFinancialAllocationTest.kt
?? shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/FinancialMappingPresenterTest.kt
```

### git diff --name-status

```text
M	.idea/gradle.xml
M	app/build.gradle.kts
M	app/src/main/AndroidManifest.xml
M	app/src/main/java/com/example/walletwise/data/repository/TransactionRepositoryImpl.kt
M	app/src/main/java/com/example/walletwise/presentation/home/AddTransactionScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/AndroidTransactionList.kt
M	app/src/main/java/com/example/walletwise/presentation/home/HistoryScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/HomeScreen.kt
M	app/src/main/java/com/example/walletwise/presentation/home/TransactionAIAssistant.kt
M	app/src/main/java/com/example/walletwise/presentation/home/TransactionViewModel.kt
M	app/src/main/java/com/example/walletwise/presentation/profile/SmartBudgetPlannerView.kt
M	firebase/checkpoint-04i/firestore.rules
M	gradlew.bat
M	shared/build.gradle.kts
M	shared/src/commonMain/kotlin/com/example/walletwise/data/mapper/FirestoreWireMapper.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/model/Transaction.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalculator.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/domain/service/BudgetCalendar.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionController.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/BudgetSessionController.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetContent.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresentation.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfileContents.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/profile/ProfilePresentation.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListContent.kt
M	shared/src/commonMain/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresentation.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/auth/AuthProfileSessionControllerTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/budget/SmartBudgetPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/profile/ProfileUiPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionListPresenterTest.kt
M	shared/src/commonTest/kotlin/com/example/walletwise/presentation/transaction/TransactionSessionControllerTest.kt
```

Output includes all pre-existing changes. No files have been staged, committed or pushed.
