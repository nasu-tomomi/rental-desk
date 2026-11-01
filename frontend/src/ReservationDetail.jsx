import { useEffect, useRef, useState } from "react";

const API_URL = "http://localhost:8080/api/reservations";
const yen = new Intl.NumberFormat("ja-JP", { maximumFractionDigits: 0 });
const display = (value) => (value == null || value === "" ? "-" : value);
const money = (value) => (value == null ? "-" : `¥${yen.format(value)}`);
const time = (value) => display(value?.slice(0, 5));
const date = (value) => display(value?.slice(0, 10));

function Fields({ items }) {
  return (
    <dl className="detail-fields">
      {items.map(([label, value]) => (
        <div key={label}>
          <dt>{label}</dt>
          <dd>{display(value)}</dd>
        </div>
      ))}
    </dl>
  );
}

export default function ReservationDetail({ id, onBack }) {
  const [reservation, setReservation] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const heading = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    heading.current?.focus();
    setLoading(true);
    setError("");
    setReservation(null);
    const url = new URL(API_URL);
    url.pathname += `/${encodeURIComponent(id)}`;
    async function load() {
      try {
        const response = await fetch(url, { signal: controller.signal });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        const data = await response.json();
        if (data == null || data.id !== id || !Array.isArray(data.users)) {
          throw new Error("Invalid response");
        }
        if (!controller.signal.aborted) setReservation(data);
      } catch (cause) {
        if (!controller.signal.aborted) {
          setError(
            "予約詳細を取得できませんでした。API（localhost:8080）の起動を確認して、一覧へ戻ってもう一度開いてください。",
          );
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => controller.abort();
  }, [id]);

  return (
    <main className="reservation-detail">
      <header>
        <p className="app-name">Rental Desk</p>
        <h1 ref={heading} tabIndex={-1}>予約詳細</h1>
        <button type="button" className="secondary" onClick={onBack}>一覧へ戻る</button>
      </header>
      {error && <p className="error" role="alert">{error}</p>}
      <p className="result-summary" role="status">
        {loading ? "読み込み中…" : error ? "取得に失敗しました" : "予約詳細"}
      </p>
      {loading || error ? (
        <div className="detail-panel empty" aria-busy={loading}>
          {loading ? "予約を読み込んでいます…" : "予約を表示できません"}
        </div>
      ) : reservation && (
        <>
          <section className="detail-panel schedule" aria-label="貸出・返却予定">
            <Fields items={[
              ["貸出日", reservation.rentalDate],
              ["予約時間", time(reservation.pickupTime)],
              ["返却予定日", date(reservation.returnDatetime)],
            ]} />
            <span className="status-badge">{display(reservation.status)}</span>
          </section>
          <section className="detail-panel" aria-labelledby="applicant-heading">
            <h2 id="applicant-heading">申込者</h2>
            <Fields items={[
              ["名前（フリガナ）", `${display(reservation.applicantName)}（${display(reservation.applicantKana)}）`],
              ["携帯電話", reservation.mobilePhone],
              ["LINE・WhatsApp ID", reservation.contactId],
              ["受取店舗", reservation.pickupStore],
              ["支払方法", reservation.paymentMethod],
            ]} />
          </section>
          <section className="detail-panel" aria-labelledby="users-heading">
            <h2 id="users-heading">利用者・料金</h2>
            <div className="table-scroll" role="region" aria-label="利用者一覧" tabIndex={0}>
              <table>
                <caption className="sr-only">利用者ごとの品目と料金</caption>
                <thead>
                  <tr>
                    {["名前", "区分", "日数", "品目", "経験", "身長", "靴サイズ", "小物", "貸出サイズメモ", "金額"].map((label) => (
                      <th key={label} scope="col">{label}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {reservation.users.length === 0 ? (
                    <tr><td colSpan={10} className="empty">利用者は登録されていません</td></tr>
                  ) : reservation.users.map((user) => (
                    <tr key={user.id}>
                      <td>{display(user.userName)}</td>
                      <td>{display(user.division)}</td>
                      <td className="number">{user.rentalDays == null ? "-" : `${user.rentalDays}日`}</td>
                      <td className="detail-text">
                        <span className="price-category">{display(user.priceCategory)}</span>
                        {display(user.priceItem)}
                      </td>
                      <td>{display(user.experience)}</td>
                      <td>{display(user.height)}</td>
                      <td>{display(user.shoeSize)}</td>
                      <td className="detail-text">{display(user.smallItems)}</td>
                      <td className="detail-text">{display(user.lentSizeMemo)}</td>
                      <td className="number amount">{money(user.amount)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <p className="detail-total">合計金額 <strong>{money(reservation.totalAmount)}</strong></p>
          </section>
          <section className="detail-panel" aria-labelledby="other-heading">
            <h2 id="other-heading">その他</h2>
            <Fields items={[
              ["スキー場", reservation.skiResort],
              ["送迎", reservation.transport],
              ["宿泊", reservation.accommodation],
              ["備考", reservation.note],
            ]} />
          </section>
        </>
      )}
    </main>
  );
}
