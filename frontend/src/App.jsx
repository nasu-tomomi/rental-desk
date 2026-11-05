import { useEffect, useRef, useState } from "react";
import { Navigate, Outlet, Route, Routes, useMatch, useNavigate } from "react-router-dom";
import { apiFetch } from "./api";
import ReservationDetail from "./ReservationDetail.jsx";
import WalkInForm from "./WalkInForm.jsx";
import LoginForm from "./LoginForm.jsx";
import StaffHeader from "./StaffHeader.jsx";

const API_URL = "http://localhost:8080/api/reservations";
const yen = new Intl.NumberFormat("ja-JP", { maximumFractionDigits: 0 });
const display = (value) => (value == null || value === "" ? "-" : value);
const money = (value) => (value == null ? "-" : `¥${yen.format(value)}`);

function Reservations({ loggingOut }) {
  const navigate = useNavigate();
  const showingList = useMatch("/") !== null;
  const [keyword, setKeyword] = useState("");
  const [rentalDate, setRentalDate] = useState("");
  const [reservations, setReservations] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const activeRequest = useRef(null);
  const searchCriteria = useRef({ keyword: "", rentalDate: "" });

  async function loadReservations(searchKeyword = "", searchDate = "") {
    searchCriteria.current = { keyword: searchKeyword, rentalDate: searchDate };
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
      const response = await apiFetch(url, { signal: controller.signal });
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
    if (!showingList) return;
    const criteria = searchCriteria.current;
    loadReservations(criteria.keyword, criteria.rentalDate);
    return () => activeRequest.current?.abort();
  }, [showingList]);

  function search(event) {
    event.preventDefault();
    loadReservations(keyword, rentalDate);
  }

  function clear() {
    setKeyword("");
    setRentalDate("");
    loadReservations();
  }

  // Keep search criteria mounted while detail and walk-in routes are displayed.
  if (!showingList) return <Outlet />;

  return (
    <main>
      <header>
        <h1>予約一覧</h1>
        <div className="walk-in-action">
          <button type="button" onClick={() => navigate("/walk-in")} disabled={loggingOut}>当日受付</button>
        </div>
      </header>
      <form onSubmit={search} className="search-form" aria-label="予約検索">
        <label htmlFor="keyword">
          氏名・フリガナ・予約番号
          <input
            id="keyword"
            type="search"
            value={keyword}
            maxLength={100}
            placeholder="例：山田・ヤマダ・12"
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
                "予約番号",
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
                <td colSpan={9} className="empty">
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
                  className={`reservation-row${reservation.status === "キャンセル" ? " reservation-row-cancelled" : ""}`}
                  onClick={() => navigate(`/reservations/${reservation.id}`)}
                >
                  <td className="number">{display(reservation.id)}</td>
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
                        navigate(`/reservations/${reservation.id}`);
                      }}
                    >
                      {`${display(reservation.applicantName)}（${display(reservation.applicantKana)}）`}
                    </button>
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
                  <td><span className={reservation.status === "キャンセル" ? "status-cancelled" : undefined}>{display(reservation.status)}</span></td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </main>
  );
}

export default function App() {
  const navigate = useNavigate();
  const [session, setSession] = useState({ status: "checking", username: null });
  const [attempt, setAttempt] = useState(0);
  const [loggingOut, setLoggingOut] = useState(false);
  const [logoutError, setLogoutError] = useState("");
  const logoutBusy = useRef(false);

  useEffect(() => {
    const controller = new AbortController();
    const expired = () => setSession({ status: "anonymous", username: null });
    window.addEventListener("auth-expired", expired);
    async function check() {
      setSession({ status: "checking", username: null });
      try {
        const response = await apiFetch("http://localhost:8080/api/auth/me", { signal: controller.signal });
        if (controller.signal.aborted) return;
        if (response.status === 401) { expired(); return; }
        if (!response.ok) throw new Error("Session check failed");
        const user = await response.json();
        if (!user?.username) throw new Error("Invalid session response");
        if (!controller.signal.aborted) setSession({ status: "authenticated", username: user.username });
      } catch {
        if (!controller.signal.aborted) setSession({ status: "error", username: null });
      }
    }
    check();
    return () => { controller.abort(); window.removeEventListener("auth-expired", expired); };
  }, [attempt]);

  async function logout() {
    if (logoutBusy.current) return;
    logoutBusy.current = true;
    setLoggingOut(true);
    setLogoutError("");
    try {
      const response = await apiFetch("http://localhost:8080/api/auth/logout", { method: "POST" });
      if (!response.ok && response.status !== 401) throw new Error("Logout failed");
      setSession({ status: "anonymous", username: null });
    } catch {
      setLogoutError("ログアウトできませんでした。通信状態を確認してもう一度お試しください。");
    } finally {
      logoutBusy.current = false;
      setLoggingOut(false);
    }
  }

  if (session.status === "checking") return <main><p role="status">ログイン状態を確認中…</p></main>;
  if (session.status === "error") return <main>
    <p role="alert">ログイン状態を確認できませんでした。通信状態を確認してください。</p>
    <button type="button" onClick={() => setAttempt((current) => current + 1)}>再試行</button>
  </main>;
  return <Routes>
    <Route path="/login" element={session.status === "anonymous" ? <LoginForm onLogin={(username) => {
      setLogoutError("");
      setSession({ status: "authenticated", username });
      navigate("/", { replace: true });
    }} /> : <Navigate to="/" replace />} />
    <Route element={session.status === "authenticated" ? <>
      <StaffHeader username={session.username} onLogout={logout} loggingOut={loggingOut} logoutError={logoutError} />
      <Outlet />
    </> : <Navigate to="/login" replace />}>
      <Route path="/" element={<Reservations loggingOut={loggingOut} />}>
        <Route index element={null} />
        <Route path="reservations/:id" element={<ReservationDetail />} />
        <Route path="walk-in" element={<WalkInForm
          onBack={() => navigate("/")}
          onCreated={(reservation) => navigate(`/reservations/${reservation.id}`)}
        />} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Route>
  </Routes>;
}
