# 予約API（開発3日目）

Java 21 / Spring Boot 3.5 / Maven / Spring JDBC。
`pom.xml` はこの `backend/` 直下に置いています。

## 起動

Docker・SQLは手元のターミナルで実行してください。既に作成済みのDBには schema / seed を再投入する必要はありません。

リポジトリのルートから：

```sh
cd backend
./mvnw spring-boot:run
```

Maven Wrapper が Maven を取得するため、初回はインターネット接続が必要です。
アプリは `http://127.0.0.1:8080` で起動します。

`backend/` を作業ディレクトリとして、既存 `.env` を読み込みます。
OSの環境変数でも上書きできます。パスワードはコードに保存していません。

| 変数 | 用途 | 省略時 |
| --- | --- | --- |
| MYSQL_DATABASE | DB名 | 必須（既存.env） |
| MYSQL_ROOT_PASSWORD | 既存rootユーザーのパスワード | MYSQL_PASSWORD未指定時に使用 |
| MYSQL_HOST | DBホスト | 127.0.0.1 |
| MYSQL_PORT | DBポート | 3306 |
| MYSQL_USER | DBユーザー | root |
| MYSQL_PASSWORD | 上記ユーザーのパスワード | MYSQL_ROOT_PASSWORD |

起動時にテーブルの作成・変更やseedの投入は行いません。

## API

| メソッド・URL | 処理 |
| --- | --- |
| POST /api/reservations | 予約と利用者をまとめて登録（201） |
| GET /api/reservations | 予約一覧（200、0件なら空配列） |
| GET /api/reservations?keyword=ヤマダ | 名前・フリガナの部分一致検索 |
| GET /api/reservations?rentalDate=2026-11-01 | 貸出日による絞り込み |

`keyword` と `rentalDate` は併用可能。本日の予約は日本時間の当日を
`rentalDate=YYYY-MM-DD` に指定します。指定しない場合は全日付が対象です。
ローマ字も名前・フリガナに保存された文字列を検索できます。かな変換・ローマ字変換は行いません。
並び順は貸出日・受取時間・IDの昇順です。一覧は現段階ではページ分割なしです。

### 登録例

以下の実行はDBに予約を1件保存します。確認したいときに手元で実行してください。
`priceMasterId` は投入済みマスタのIDに合わせてください（通常、seedを空のテーブルに投入した場合は1がおとなのスタンダードセット）。

```sh
curl -i http://127.0.0.1:8080/api/reservations \
  -H 'Content-Type: application/json' \
  --data '{
    "source": "net",
    "applicantName": "山田 Alice",
    "applicantKana": "ヤマダ",
    "mobilePhone": "09012345678",
    "rentalDate": "2026-11-01",
    "pickupTime": "09:00:00",
    "returnDatetime": "2026-11-03T17:00:00",
    "users": [{
      "userName": "Alice",
      "division": "おとな",
      "rentalDays": 3,
      "priceMasterId": 1,
      "height": "170",
      "shoeSize": "26.5"
    }]
  }'
```

必須は `source`（net / walk-in）、`applicantName`、`rentalDate`、
`users`（1〜20人）、各利用者の `division`、`rentalDays`（1〜365）、`priceMasterId`。
返却日時を指定した場合は貸出日時以降である必要があります。
利用日数は各利用者の指定値から計算します。返却日時からの自動算出は行いません。

登録結果はID・ステータス・計算済み金額・利用者を含むJSONです。
ステータスは必ず「予約済」。金額はクライアントから受け取らずマスタから計算します。
1日=1日料金、2日=2日料金、3日以上=2日料金+(日数-2)×追加料金。
利用者の区分とマスタの区分が違う場合、またはマスタが存在しない場合は400です。
予約と利用者は1つのトランザクションで保存し、途中失敗時は全件取り消します。

今回は各利用者の `priceMasterId` で指定した主なレンタル品を計算します。
`smallItems` は既存テーブルの自由記述として保存し、そこからの小物追加料金の計算は未実装です。
削除・ステータス変更・フロントエンドも今回の範囲には含めていません。

### 一覧・検索例

```sh
curl 'http://127.0.0.1:8080/api/reservations'
curl -G 'http://127.0.0.1:8080/api/reservations' \
  --data-urlencode 'keyword=ヤマダ' \
  --data-urlencode 'rentalDate=2026-11-01'
```

## テスト

```sh
cd backend  # リポジトリのルートからの場合
./mvnw test
```

テスト専用のインメモリH2を使い、既存MySQL・Docker・.envには接続しません。
プロジェクトのschemaとseedをテストリソースとして読み、MySQL固有DDLだけH2用に変換します。
登録・一覧・検索・入力チェック・料金計算・保存失敗時のロールバックを確認します。
MySQL実環境での接続と動作は、手元で起動した後に上記コマンドで確認してください。
