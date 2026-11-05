import { useEffect, useRef, useState } from "react";
import { apiFetch } from "./api";

export default function LoginForm({ onLogin }) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [visible, setVisible] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const pending = useRef(null);
  const busy = useRef(false);

  useEffect(() => () => pending.current?.abort(), []);

  async function submit(event) {
    event.preventDefault();
    if (busy.current) return;
    busy.current = true;
    setSaving(true);
    setError("");
    const controller = new AbortController();
    pending.current = controller;
    try {
      const response = await apiFetch("http://localhost:8080/api/auth/login", {
        method: "POST", signal: controller.signal,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ username, password }),
      });
      if (response.status === 401) throw new Error("ユーザー名またはパスワードが違います。");
      if (!response.ok) throw new Error("ログインできませんでした。時間をおいてもう一度お試しください。");
      const user = await response.json();
      if (!user?.username) throw new Error("ログイン結果を確認できませんでした。");
      if (!controller.signal.aborted) onLogin(user.username);
    } catch (cause) {
      if (!controller.signal.aborted) setError(cause instanceof TypeError
        ? "接続できませんでした。通信状態を確認してもう一度お試しください。" : cause.message);
    } finally {
      if (!controller.signal.aborted) { busy.current = false; setSaving(false); }
    }
  }

  return <main className="login-page">
    <header><p className="app-name">Rental Desk</p><h1>スタッフログイン</h1></header>
    <form className="detail-panel login-form" onSubmit={submit}>
      <fieldset disabled={saving}>
        <legend className="sr-only">スタッフのログイン情報</legend>
        <label htmlFor="login-username">ユーザー名
          <input id="login-username" name="username" autoComplete="username" required maxLength={100}
            value={username} onChange={(event) => setUsername(event.target.value)} />
        </label>
        <label htmlFor="login-password">パスワード</label>
        <div className="password-field">
          <input id="login-password" name="password" type={visible ? "text" : "password"}
            autoComplete="current-password" required maxLength={200}
            value={password} onChange={(event) => setPassword(event.target.value)} />
          <button type="button" className="secondary" aria-controls="login-password" aria-pressed={visible}
            onClick={() => setVisible((current) => !current)}>{visible ? "非表示" : "表示"}</button>
        </div>
        <button type="submit">ログイン</button>
      </fieldset>
      {error && <p className="error" role="alert">{error}</p>}
      <p role="status">{saving ? "ログイン中…" : ""}</p>
    </form>
  </main>;
}
