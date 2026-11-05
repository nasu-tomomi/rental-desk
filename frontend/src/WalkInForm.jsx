import { useEffect, useRef, useState } from "react";
import { apiFetch } from "./api";
import { additionalItemNames } from "./rentalItems";

function today() {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, "0")}-${String(now.getDate()).padStart(2, "0")}`;
}

const smallItemOptions = ["ゴーグル", "ヘルメット", "グローブ", "ニットキャップ", "長靴", "スキーストック"];
const newUser = () => ({ key: crypto.randomUUID(), userName: "", priceMasterId: "", rentalDays: 1, height: "", shoeSize: "", experience: "", selectedItems: [] });

export default function WalkInForm({ onBack, onCreated }) {
  const [applicant, setApplicant] = useState({ applicantName: "", applicantKana: "", mobilePhone: "", contactId: "", staffMemo: "", rentalDate: today() });
  const [users, setUsers] = useState(() => [newUser()]);
  const [prices, setPrices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [priceError, setPriceError] = useState("");
  const [error, setError] = useState("");
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);
  const activeSave = useRef(null);
  const heading = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    heading.current?.focus();
    async function loadPrices() {
      try {
        const response = await apiFetch("http://localhost:8080/api/prices", { signal: controller.signal });
        if (!response.ok) throw new Error();
        const data = await response.json();
        if (!Array.isArray(data) || !data.length) throw new Error();
        if (!controller.signal.aborted) setPrices(data);
      } catch {
        if (!controller.signal.aborted) setPriceError("料金表を取得できませんでした。一覧へ戻ってもう一度開いてください。");
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    loadPrices();
    return () => { controller.abort(); activeSave.current?.abort(); };
  }, []);

  function editUser(index, field, value) {
    setUsers((current) => current.map((user, i) => i === index ? { ...user, [field]: value } : user));
    setError("");
  }

  async function submit(event) {
    event.preventDefault();
    if (savingRef.current || loading || priceError) return;
    if (!applicant.applicantName.trim() || !applicant.mobilePhone.trim() || !applicant.contactId.trim() || users.some((user) => !user.userName.trim())) {
      setError("申込者名・携帯電話・LINE・WhatsApp ID・利用者名を入力してください。");
      return;
    }
    savingRef.current = true;
    setSaving(true);
    setError("");
    const controller = new AbortController();
    activeSave.current = controller;
    try {
      const response = await apiFetch("http://localhost:8080/api/reservations", {
        method: "POST", signal: controller.signal,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          ...applicant, applicantName: applicant.applicantName.trim(), mobilePhone: applicant.mobilePhone.trim(), contactId: applicant.contactId.trim(), source: "walk-in",
          users: users.map(({ key, selectedItems, ...user }) => ({
            ...user, userName: user.userName.trim(), priceMasterId: Number(user.priceMasterId),
            additionalPriceMasterIds: selectedItems.map((item) => {
              const division = prices.find((price) => price.id === Number(user.priceMasterId))?.division;
              const price = prices.find((price) => price.category === "スタンダード" && price.item === item && price.division === division);
              if (!price) throw new Error(`${item}の料金が見つかりません。品目と料金表を確認してください。`);
              return price.id;
            }),
            smallItems: selectedItems.join("／"),
            rentalDays: Number(user.rentalDays),
            division: prices.find((price) => price.id === Number(user.priceMasterId))?.division,
          })),
        }),
      });
      const data = await response.json().catch(() => null);
      if (!response.ok) throw new Error(data?.detail || `通信に失敗しました（HTTP ${response.status}）`);
      if (!data?.id) throw new Error("登録結果を確認できませんでした。");
      if (!controller.signal.aborted) onCreated(data);
    } catch (cause) {
      if (!controller.signal.aborted) setError(`登録できませんでした。${cause.message}`);
    } finally {
      if (!controller.signal.aborted) { savingRef.current = false; setSaving(false); }
    }
  }

  return (
    <main className="walk-in-form">
      <header>
        <h1 ref={heading} tabIndex={-1}>当日受付</h1>
        <button type="button" className="secondary" onClick={onBack} disabled={saving}>一覧へ戻る</button>
      </header>
      {priceError && <p className="error" role="alert">{priceError}</p>}
      <form onSubmit={submit}>
        <fieldset disabled={saving} className="registration-fields">
          <legend className="sr-only">当日受付の登録情報</legend>
          <section className="detail-panel">
            <h2>申込者</h2>
            <div className="registration-grid">
              {[["applicantName", "名前（必須）", "text", 100, true], ["applicantKana", "フリガナ", "text", 100, false], ["mobilePhone", "携帯電話（必須）", "tel", 30, true], ["contactId", "LINE・WhatsApp ID（必須）", "text", 100, true], ["rentalDate", "貸出日（必須）", "date", undefined, true]].map(([field, label, type, maxLength, required]) => (
                <label key={field}>{label}<input type={type} required={required} maxLength={maxLength} value={applicant[field]}
                  onChange={(event) => { setApplicant((current) => ({ ...current, [field]: event.target.value })); setError(""); }} /></label>
              ))}
            </div>
            <label className="staff-memo" htmlFor="registration-staff-memo">スタッフメモ
              <textarea id="registration-staff-memo" rows={4} maxLength={10000} value={applicant.staffMemo}
                onChange={(event) => { setApplicant((current) => ({ ...current, staffMemo: event.target.value })); setError(""); }} />
            </label>
          </section>
          {users.map((user, index) => (
            <section className="detail-panel" key={user.key} aria-labelledby={`user-${index}`}>
              <h2 id={`user-${index}`}>利用者 {index + 1}</h2>
              <button type="button" className="secondary user-remove" disabled={users.length === 1} aria-label={`利用者${index + 1}を削除`}
                onClick={() => { setUsers((current) => current.filter((entry) => entry.key !== user.key)); setError(""); }}>削除</button>
              <div className="registration-grid">
                <label>名前（必須）<input required maxLength={100} value={user.userName} onChange={(event) => editUser(index, "userName", event.target.value)} /></label>
                <label>品目（必須）<select required disabled={loading || !!priceError} value={user.priceMasterId} onChange={(event) => editUser(index, "priceMasterId", event.target.value)}>
                  <option value="">{loading ? "料金表を読み込み中…" : "品目を選択してください"}</option>
                  {prices.filter((price) => !additionalItemNames.includes(price.item)).map((price) => <option key={price.id} value={price.id}>{price.division} / {price.category} / {price.item}</option>)}
                </select></label>
                <label>日数（必須）<input type="number" required min={1} max={365} step={1} value={user.rentalDays} onChange={(event) => editUser(index, "rentalDays", event.target.value)} /></label>
                {[["height", "身長 (cm)", 20], ["shoeSize", "靴サイズ (cm)", 20]].map(([field, label, maxLength]) => (
                  <label key={field}>{label}<input maxLength={maxLength} value={user[field]} onChange={(event) => editUser(index, field, event.target.value)} /></label>
                ))}
                <label>経験<select value={user.experience} onChange={(event) => editUser(index, "experience", event.target.value)}>
                  <option value="">選択してください（任意）</option>
                  {["経験なし", "初級", "中級", "上級"].map((value) => <option key={value} value={value}>{value}</option>)}
                </select></label>
              </div>
              <fieldset className="small-items">
                <legend>小物の追加レンタル（複数選択可）</legend>
                <div className="item-chips">
                  {smallItemOptions.map((item) => <button key={item} type="button" className="item-chip" disabled={loading || !!priceError || !user.priceMasterId} aria-pressed={user.selectedItems.includes(item)}
                    onClick={() => editUser(index, "selectedItems", user.selectedItems.includes(item) ? user.selectedItems.filter((value) => value !== item) : [...user.selectedItems, item])}>{item}</button>)}
                </div>
              </fieldset>
            </section>
          ))}
          <div className="actions registration-actions">
            <button type="button" className="secondary" disabled={users.length >= 20} onClick={() => setUsers((current) => [...current, newUser()])}>利用者を追加</button>
            <button type="submit" disabled={loading || !!priceError}>登録する</button>
          </div>
          <p>利用者は最大20名まで登録できます。金額は登録時に計算されます。</p>
        </fieldset>
        {error && <p className="error" role="alert">{error}</p>}
        <p role="status">{saving ? "登録中…" : ""}</p>
      </form>
    </main>
  );
}
