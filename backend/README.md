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
| GET /api/prices | 料金マスタ全件（ID昇順） |
| PATCH /api/reservations/{id}/users | 利用者の品目・日数・貸出サイズメモを更新 |
| POST /api/reservations | 予約と利用者をまとめて登録（201） |
| GET /api/reservations/{id} | 予約詳細 |
| PATCH /api/reservations/{id}/status | ステータス更新、更新後の予約詳細を返却 |
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

各利用者の金額は、メイン品目と `reservation_user_items` の追加品目の料金の合計です。
どの品目も利用者の区分・利用日数で計算し、区分の不一致は400です。
`smallItems` は表示・メモ用の文字列として保存し、料金計算には使いません。
追加品目は各利用者の `additionalPriceMasterIds` で指定します（下記参照）。
予約削除時は追加品目、利用者、予約の順に、1つのトランザクションで削除します。

### 料金一覧・利用者更新

`GET /api/prices` は料金マスタ全行を配列で返します（0件なら `[]`）。
JSONのキーは既存APIと同じcamelCaseで、`id`、`division`、`category`、`item`、
`price1day`、`price2day`、`priceAfter3rd` です。
開発用フロントエンドからのGETもCORSで許可しています。

`PATCH /api/reservations/{id}/users` の入力例：

```json
{"users":[{"id":1,"priceMasterId":3,"rentalDays":2,"lentSizeMemo":null}]}
```

指定された利用者だけを更新し、省略された利用者は保持します。
`lentSizeMemo` はnull（または省略）でクリアできます。最大10000文字です。
`users` は1〜20件、IDは重複不可、`rentalDays` は1〜365日です。
料金マスタは登録時と同様に利用者の区分と一致する必要があります。
金額はサーバー側でマスタから計算し、未更新の利用者の金額も含めて予約合計を保存します。
利用者更新と合計更新は1トランザクションで行い、同じ予約への編集を直列化します。
存在しない予約は404、予約に属さない利用者・不正な料金マスタ・入力値は400（ProblemDetail）です。
成功時はGET詳細と同じ予約詳細を返します。

### ステータス更新

`PATCH /api/reservations/{id}/status` に `{"status":"受付済"}` のようなJSONを送ります。
「予約済→受付済→貸出中→返却済」の1段階ずつの遷移のみ許可し、同じ状態・逆戻り・飛び越し・不正な入力は400です。
存在しないIDは404です。エラーはProblemDetail形式、成功時はGET詳細と同じ形式で更新後の予約を返します。
開発用フロントエンド（`http://localhost:5173`）からのCORSはGET・PATCHを許可します。

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
登録・詳細・一覧・検索・ステータス更新・料金一覧・利用者更新・入力チェック・料金計算・保存失敗時のロールバックを確認します。
MySQL実環境での接続と動作は、手元で起動した後に上記コマンドで確認してください。

## 追加品目テーブル（Phase 2）

新規DBは `sql/01-schema.sql` に `reservation_user_items` を含みます。
既存DBには `sql/04-create-reservation-user-items.sql` を手動実行してください。
既存の予約・利用者データは保持され、同SQLは再実行できます。

`reservation_user_items` は `id`（PK）、`reservation_user_id`（利用者FK）、
`price_master_id`（料金マスタFK）を持ちます。Spring JDBC の record と Repository で扱います。
予約削除時は追加品目も削除します。料金計算はPhase 3で実装済みです。追加品目を指定するAPIはPhase 4で実装済みです。

## 追加品目API（Phase 4）

POST `/api/reservations` の各利用者に `additionalPriceMasterIds` を指定できます。
IDは GET `/api/prices` から取得し、利用者の区分に一致する行を選びます。
省略またはnullなら追加品目なし、空配列も追加品目なしです。

```json
{
  "source": "walk-in",
  "applicantName": "Alice",
  "rentalDate": "2026-11-04",
  "users": [{
    "userName": "Alice",
    "division": "おとな",
    "rentalDays": 3,
    "priceMasterId": 1,
    "additionalPriceMasterIds": [23, 25],
    "smallItems": "グローブ／ゴーグル／自由メモ"
  }]
}
```

上記IDは初期seedを空のDBに投入した場合の例です。
メイン10,000円＋グローブ2,000円＋ゴーグル2,000円＝14,000円（税込）になります。
`smallItems` は送信された文字列をそのまま保存します。
当日受付フォームではチップ名と自由入力をこの文字列にまとめ、チップのIDを配列にも送ります（フロント対応はPhase 5）。
文字列からの料金計算や、既存メモから追加品目への自動変換は行いません。

GET `/api/reservations/{id}`、POST・PATCHの成功レスポンスでは、
各利用者に `additionalItems` を返します。追加品目なしなら `[]` です。
既存の `priceItem` はメイン品目、`amount` は追加品目を含めた利用者合計です。

```json
{
  "priceItem": "スキー OR スノーボード セット",
  "amount": 14000,
  "additionalItems": [
    {"priceMasterId": 23, "category": "スタンダード", "item": "グローブ", "amount": 2000},
    {"priceMasterId": 25, "category": "スタンダード", "item": "ゴーグル", "amount": 2000}
  ]
}
```

PATCH `/api/reservations/{id}/users` は追加品目の配列で指定利用者の選択を置き換えます。
配列を省略またはnullにすると既存選択を保持し、`[]` で全解除します。
既存どおり、リクエストから省略された利用者は保持されます。

```json
{"users":[{"id":1,"priceMasterId":1,"rentalDays":2,"lentSizeMemo":null,"additionalPriceMasterIds":[29]}]}
```

追加品目も利用者と同じ日数で再計算し、利用者金額と予約合計を保存します。
`smallItems` のメモはPATCHでは変更しません。料金用の選択は `additionalItems` で確認します。
存在しないID・区分不一致・重複ID・null要素・0以下のIDは400です。
保存は予約単位のトランザクションで、途中失敗時は追加品目・利用者金額・予約合計も取り消します。
