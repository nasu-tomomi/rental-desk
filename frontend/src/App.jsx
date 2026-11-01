import { useEffect, useRef, useState } from "react";
import ReservationDetail from "./ReservationDetail.jsx";

const API_URL = "http://localhost:8080/api/reservations";
const yen = new Intl.NumberFormat("ja-JP", { maximumFractionDigits: 0 });
const display = (value) => (value == null || value === "" ? "-" : value);
const money = (value) => (value == null ? "-" : `¥${yen.format(value)}`);

export default function App() {
  const [selectedId, setSelectedId] = useState(null);
  const [keyword, setKeyword] = useState("");
  const [rentalDate, setRentalDate] = useState("");
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const activeRequest = useRef(null);

  async function loadReservations(searchKeyword = "", searchDate = "") {
    activeRequest.current?.abort();
    const controller = new AbortController();
    activeRequest.current = controller;
    setLoading(true);
    setError("");
    // Clear previous results so a failed search cannot look like a successful one.
    setReservations([]);
    const url = new URL(API_URL);
    if (searchKeyword.trim())
      url.searchParams.set("keyword", searchKeyword.trim());
    if (searchDate) url.searchParams.set("rentalDate", searchDate);
    try {
      const response = await fetch(url, { signal: controller.signal });
      if (!response.ok) throw new Error(`HTTP ${response.status}`);
      const data = await response.json();
      if (!Array.isArray(data)) throw new Error("Invalid response");
      if (!controller.signal.aborted) setReservations(data);
    } catch (cause) {
      if (!controller.signal.aborted) {
        setError(
          "予約一覧を取得できませんでした。API（localhost:8080）の起動を確認して、もう一度検索してください。",
        );
      }
    } finally {
      if (!controller.signal.aborted) setLoading(false);
    }
  }

  useEffect(() => {
    loadReservations();
    return () => activeRequest.current?.abort();
  }, []);

  function search(event) {
    event.preventDefault();
    loadReservations(keyword, rentalDate);
  }

  function clear() {
    setKeyword("");
    setRentalDate("");
    loadReservations();
  }

  if (selectedId !== null) {
    return (
      <ReservationDetail
        id={selectedId}
        onBack={() => setSelectedId(null)}
      />
    );
  }

  return (
    <main>
      <header>
        <p className="app-name">Rental Desk</p>
        <h1>予約一覧</h1>
      </header>
      <form onSubmit={search} className="search-form" aria-label="予約検索">
        <label htmlFor="keyword">
          検索語（氏名・フリガナ）
          <input
            id="keyword"
            type="search"
            value={keyword}
            maxLength={100}
            placeholder="例：山田・ヤマダ・Alice"
            onChange={(event) => setKeyword(event.target.value)}
          />
        </label>
        <label htmlFor="rental-date">
          貸出日
          <input
            id="rental-date"
            type="date"
            value={rentalDate}
            onChange={(event) => setRentalDate(event.target.value)}
          />
        </label>
        <div className="actions">
          <button type="submit">検索</button>
          <button type="button" className="secondary" onClick={clear}>
            クリア
          </button>
        </div>
      </form>
      {error && (
        <p className="error" role="alert">
          {error}
        </p>
      )}
      <p className="result-summary" role="status">
        {loading
          ? "読み込み中…"
          : error
            ? "取得に失敗しました"
            : `${reservations.length}件の予約`}
      </p>
      <div
        className="table-scroll"
        role="region"
        aria-label="予約一覧"
        tabIndex={0}
      >
        <table aria-busy={loading}>
          <caption className="sr-only">予約検索結果</caption>
          <thead>
            <tr>
              {[
                "貸出日",
                "予約時間",
                "申込者",
                "携帯電話",
                "利用人数",
                "合計金額",
                "支払方法",
                "ステータス",
              ].map((heading) => (
                <th key={heading} scope="col">
                  {heading}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {loading || error || reservations.length === 0 ? (
              <tr>
                <td colSpan={8} className="empty">
                  {loading
                    ? "予約を読み込んでいます…"
                    : error
                      ? "予約を表示できません"
                      : "該当する予約はありません"}
                </td>
              </tr>
            ) : (
              reservations.map((reservation) => (
                <tr
                  key={reservation.id}
                  className="reservation-row"
                  onClick={() => setSelectedId(reservation.id)}
                >
                  <td>{display(reservation.rentalDate)}</td>
                  <td>
                    {reservation.pickupTime == null
                      ? "-"
                      : display(reservation.pickupTime.slice(0, 5))}
                  </td>
                  <td className="applicant">
                    <button
                      type="button"
                      className="reservation-link"
                      aria-label={`${display(reservation.applicantName)}の予約詳細`}
                      onClick={(event) => {
                        event.stopPropagation();
                        setSelectedId(reservation.id);
                      }}
                    >
                      {display(reservation.applicantName)}
                    </button>
                    <span className="kana">
                      {display(reservation.applicantKana)}
                    </span>
                  </td>
                  <td>{display(reservation.mobilePhone)}</td>
                  <td className="number">
                    {reservation.users == null
                      ? "-"
                      : `${reservation.users.length}人`}
                  </td>
                  <td className="number amount">
                    {money(reservation.totalAmount)}
                  </td>
                  <td>{display(reservation.paymentMethod)}</td>
                  <td>{display(reservation.status)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </main>
  );
}
