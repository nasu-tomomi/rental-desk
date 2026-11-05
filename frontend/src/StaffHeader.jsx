export default function StaffHeader({ username, onLogout, loggingOut, logoutError }) {
  return <header className="staff-header" aria-label="共通ヘッダー">
    <div className="staff-header-inner">
      <span className="staff-header-brand">Rental Desk</span>
      <div className="staff-header-account">
        <span>ログイン中：{username}</span>
        <button type="button" className="secondary" onClick={onLogout} disabled={loggingOut}>
          {loggingOut ? "ログアウト中…" : "ログアウト"}
        </button>
      </div>
    </div>
    {logoutError && <p className="error staff-header-error" role="alert">{logoutError}</p>}
  </header>;
}
