# 予約一覧フロントエンド

React + Vite（JavaScript）。別のターミナルで既存のSpring Boot APIを起動してください。
Docker・SQLの操作やDB変更は不要です。

リポジトリのルートから：

```sh
cd frontend
npm install
npm run dev
```

ブラウザで http://localhost:5173 を開いてください。
APIのCORS設定はこのオリジンだけを許可しています。
5173番ポートが使用中の場合は自動で別ポートに切り替えず、エラーで停止します。

- 起動時：`GET http://localhost:8080/api/reservations`
- 検索：`keyword` と `rentalDate` を指定して再取得（空欄は省略）
- 日本語は `URL.searchParams` でエンコード
- クリア：入力欄を空にしてパラメータなしで全件を再取得
- null・空文字は `-`、利用人数は `users.length`、金額は `¥` と桁区切りで表示
- 「受付時間」列には既存APIの `pickupTime` を表示
- 読み込み中・0件・取得失敗を表示

```sh
npm run build
```

本番向けビルドを確認できます。APIはローカルの8080番を直接参照する開発用構成です。
