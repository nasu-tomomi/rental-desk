import { useEffect, useRef, useState } from "react";
import { apiFetch } from "./api";
import { additionalItemNames } from "./rentalItems";

const API_URL = "http://localhost:8080/api/reservations";
const yen = new Intl.NumberFormat("ja-JP", { maximumFractionDigits: 0 });
const display = (value) => (value == null || value === "" ? "-" : value);
const money = (value) => (value == null ? "-" : `¥${yen.format(value)}（税込）`);
const time = (value) => display(value?.slice(0, 5));
const date = (value) => display(value?.slice(0, 10));

const additionalIds = (user) => user.additionalPriceMasterIds ?? (user.additionalItems ?? []).map((item) => item.priceMasterId);

const editableUser = (user) => ({
  id: user.id, priceMasterId: Number(user.priceMasterId), rentalDays: Number(user.rentalDays),
  lentSizeMemo: user.lentSizeMemo || "", additionalPriceMasterIds: [...additionalIds(user)].sort((a, b) => a - b),
});

function itemEstimate(priceId, user, prices) {
  const price = prices.find((p) => p.id === Number(priceId) && p.division === user.division);
  const days = Number(user.rentalDays);
  if (!price || !Number.isInteger(days) || days < 1 || days > 365) return null;
  const amount = days === 1 ? price.price1day : price.price2day + (days - 2) * price.priceAfter3rd;
  return Number.isSafeInteger(amount) && amount >= 0 && amount <= 2147483647 ? amount : null;
}

function estimate(user, prices) {
  const amounts = [user.priceMasterId, ...additionalIds(user)].map((id) => itemEstimate(id, user, prices));
  if (amounts.some((amount) => amount == null)) return null;
  const total = amounts.reduce((sum, amount) => sum + amount, 0);
  return total <= 2147483647 ? total : null;
}

async function requestJson(url, options) {
  const response = await apiFetch(url, options);
  const data = await response.json().catch(() => null);
  if (!response.ok) throw new Error(data?.detail || `通信に失敗しました（HTTP ${response.status}）`);
  return data;
}

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
  const [prices, setPrices] = useState([]);
  const [users, setUsers] = useState([]);
  const [staffMemo, setStaffMemo] = useState("");
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState("");
  const [message, setMessage] = useState("");
  const savingRef = useRef(false);
  const activeSave = useRef(null);
  const heading = useRef(null);

  useEffect(() => {
    const controller = new AbortController();
    heading.current?.focus();
    setLoading(true);
    setError("");
    setReservation(null);
    setPrices([]);
    setUsers([]);
    setStaffMemo("");
    setSaveError("");
    setMessage("");
    setSaving(false);
    savingRef.current = false;
    const url = new URL(API_URL);
    url.pathname += `/${encodeURIComponent(id)}`;
    async function load() {
      try {
        const [data, priceData] = await Promise.all([
          requestJson(url, { signal: controller.signal }),
          requestJson("http://localhost:8080/api/prices", { signal: controller.signal }),
        ]);
        if (!Array.isArray(priceData)) throw new Error("Invalid prices");
        if (data == null || data.id !== id || !Array.isArray(data.users)) {
          throw new Error("Invalid response");
        }
        if (!controller.signal.aborted) {
          setReservation(data);
          setUsers(data.users);
          setStaffMemo(data.staffMemo ?? "");
          setPrices(priceData);
        }
      } catch (cause) {
        if (!controller.signal.aborted) {
          setError(
            "予約詳細または料金表を取得できませんでした。API（localhost:8080）の起動を確認して、一覧へ戻ってもう一度開いてください。",
          );
        }
      } finally {
        if (!controller.signal.aborted) setLoading(false);
      }
    }
    load();
    return () => {
      controller.abort();
      activeSave.current?.abort();
    };
  }, [id]);

  const amounts = users.map((user) => estimate(user, prices));
  const total = amounts.some((amount) => amount == null) ? null : amounts.reduce((sum, amount) => sum + amount, 0);
  const hasChanges = staffMemo !== (reservation?.staffMemo ?? "")
    || JSON.stringify(users.map(editableUser)) !== JSON.stringify((reservation?.users ?? []).map(editableUser));
  const displayedTotal = hasChanges ? total : reservation?.totalAmount;
  const valid = users.length > 0 && total != null && total <= 2147483647;
  const statusActions = {
    "予約済": { status: "受付済", label: "受付する" },
    "受付済": { status: "貸出中", label: "貸し出す" },
    "貸出中": { status: "返却済", label: "返却する" },
  };
  const nextAction = statusActions[reservation?.status];
  const canCancel = reservation?.status === "予約済" || reservation?.status === "受付済";
  const readOnly = reservation?.status === "キャンセル" || reservation?.status === "返却済";

  function editUser(userId, field, value) {
    setUsers((current) => current.map((user) => user.id === userId ? { ...user, [field]: value } : user));
    setMessage("");
    setSaveError("");
  }

  async function save(status = null) {
    if (savingRef.current || readOnly || !valid) return;
    if (status === "キャンセル" && !window.confirm(`予約番号：${id}をキャンセルします。よろしいですか？`)) return;
    savingRef.current = true;
    setSaving(true);
    setSaveError("");
    setMessage("");
    const controller = new AbortController();
    activeSave.current = controller;
    let saved = false;
    try {
      const updated = await requestJson(`${API_URL}/${encodeURIComponent(id)}/users`, {
        method: "PATCH", signal: controller.signal,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ staffMemo, users: users.map((user) => ({
          id: user.id, priceMasterId: Number(user.priceMasterId), rentalDays: Number(user.rentalDays),
          lentSizeMemo: user.lentSizeMemo || null,
          additionalPriceMasterIds: additionalIds(user),
        })) }),
      });
      if (controller.signal.aborted) return;
      setReservation(updated);
      setUsers(updated.users);
      setStaffMemo(updated.staffMemo ?? "");
      saved = true;
      if (status) {
        const advanced = await requestJson(`${API_URL}/${encodeURIComponent(id)}/status`, {
          method: "PATCH", signal: controller.signal,
          headers: { "Content-Type": "application/json" }, body: JSON.stringify({ status }),
        });
        if (controller.signal.aborted) return;
        setReservation(advanced);
        setUsers(advanced.users);
        setStaffMemo(advanced.staffMemo ?? "");
      }
      setMessage(status ? `変更を保存し、${status}に更新しました。` : "変更を保存しました。");
    } catch (cause) {
      if (!controller.signal.aborted) {
        setSaveError(`${saved ? "利用者の変更は保存済みですが、ステータスを更新できませんでした。" : "変更を保存できませんでした。"}${cause.message}`);
      }
    } finally {
      if (!controller.signal.aborted) {
        savingRef.current = false;
        setSaving(false);
      }
    }
  }

  async function deleteReservation() {
    if (savingRef.current || !window.confirm(`予約番号：${id}を削除します。利用者情報も削除されます。よろしいですか？`)) return;
    savingRef.current = true;
    setSaving(true);
    setSaveError("");
    setMessage("");
    const controller = new AbortController();
    activeSave.current = controller;
    try {
      await requestJson(`${API_URL}/${encodeURIComponent(id)}`, {
        method: "DELETE", signal: controller.signal,
      });
      if (!controller.signal.aborted) onBack();
    } catch (cause) {
      if (!controller.signal.aborted) setSaveError(`予約を削除できませんでした。${cause.message}`);
    } finally {
      if (!controller.signal.aborted) {
        savingRef.current = false;
        setSaving(false);
      }
    }
  }

  return (
    <main className="reservation-detail">
      <header>
        <h1 ref={heading} tabIndex={-1}>予約詳細</h1>
        {reservation && <p>予約番号：{reservation.id}</p>}
        <button type="button" className="secondary" onClick={onBack} disabled={saving}>一覧へ戻る</button>
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
            <p className="estimate-note" id="estimate-note">編集中の金額は見込みです。保存時に最新の料金表で再計算して確定します。</p>
            <fieldset className="user-editor" disabled={saving || readOnly} aria-describedby="estimate-note">
            <legend className="sr-only">利用者の品目・日数・貸出サイズメモを編集</legend>
            <div className="table-scroll" role="region" aria-label="利用者一覧" tabIndex={0}>
              <table>
                <caption className="sr-only">利用者ごとの品目と料金</caption>
                <thead>
                  <tr>
                    {["名前", "区分", "日数", "メイン品目", "経験", "身長 (cm)", "靴サイズ (cm)", "貸出サイズメモ"].map((label) => (
                      <th key={label} scope="col">{label}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {users.length === 0 ? (
                    <tr><td colSpan={8} className="empty">利用者は登録されていません</td></tr>
                  ) : users.map((user, index) => (
                    <tr key={user.id}>
                      <td>{display(user.userName)}</td>
                      <td>{display(user.division)}</td>
                      <td className="number"><input className="days-input" type="number" min="1" max="365" step="1"
                        aria-label={`${user.userName || `利用者${index + 1}`}の日数`} value={user.rentalDays ?? ""}
                        onChange={(event) => editUser(user.id, "rentalDays", event.target.value)} /></td>
                      <td className="detail-text">
                        <select aria-label={`${user.userName || `利用者${index + 1}`}の品目`}
                          value={user.priceMasterId ?? ""} onChange={(event) => editUser(user.id, "priceMasterId", event.target.value)}>
                          {!prices.some((price) => price.id === Number(user.priceMasterId) && price.division === user.division) &&
                            <option value={user.priceMasterId ?? ""}>品目を選択してください</option>}
                          {prices.filter((price) => price.division === user.division && !additionalItemNames.includes(price.item)).map((price) => (
                            <option key={price.id} value={price.id}>{price.category} / {price.item}</option>
                          ))}
                        </select>
                      </td>
                      <td>{display(user.experience)}</td>
                      <td>{display(user.height)}</td>
                      <td>{display(user.shoeSize)}</td>
                      <td className="detail-text"><input type="text" maxLength={10000}
                        aria-label={`${user.userName || `利用者${index + 1}`}の貸出サイズメモ`} value={user.lentSizeMemo ?? ""}
                        onChange={(event) => editUser(user.id, "lentSizeMemo", event.target.value)} /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {users.map((user, index) => {
              const selected = additionalIds(user);
              const mainPrice = prices.find((price) => price.id === Number(user.priceMasterId));
              const label = user.userName || `利用者${index + 1}`;
              const options = prices.filter((price) => price.division === user.division);
              const common = options.filter((price) => price.category === "スタンダード" && additionalItemNames.includes(price.item));
              const choice = (price) => (
                <label key={price.id} className="additional-choice">
                  <input type="checkbox" checked={selected.includes(price.id)}
                    aria-label={`${label}の追加品目：${price.item}`}
                    onChange={(event) => editUser(user.id, "additionalPriceMasterIds", event.target.checked
                      ? [...selected, price.id] : selected.filter((id) => id !== price.id))} />
                  <span>{price.item}<small>{money(itemEstimate(price.id, user, prices))}</small></span>
                </label>
              );
              return <section key={user.id} className="user-breakdown" aria-label={`${label}の料金内訳と追加品目`}>
                <h3>{label}：料金内訳</h3>
                <dl className="price-breakdown">
                  <div><dt>メイン：{mainPrice ? `${mainPrice.category} / ${mainPrice.item}` : "未選択"}</dt>
                    <dd>{money(itemEstimate(user.priceMasterId, user, prices))}</dd></div>
                  {selected.map((priceId) => <div key={priceId}>
                    <dt>追加：{prices.find((price) => price.id === priceId)?.item || `品目ID ${priceId}`}</dt>
                    <dd>{money(itemEstimate(priceId, user, prices))}</dd>
                  </div>)}
                  {!selected.length && <div><dt>追加品目</dt><dd>なし</dd></div>}
                  <div className="breakdown-total"><dt>利用者合計</dt><dd>{money(amounts[index])}</dd></div>
                </dl>
                <fieldset className="additional-options">
                  <legend>{label}の追加品目（{user.division}・{user.rentalDays}日間）</legend>
                  <div className="additional-grid">{common.map(choice)}</div>
                </fieldset>
              </section>;
            })}
            <label className="staff-memo" htmlFor="staff-memo">スタッフメモ
              <textarea id="staff-memo" rows={4} maxLength={10000} value={staffMemo}
                placeholder="例：グローブ追加、料金徴収済み"
                onChange={(event) => { setStaffMemo(event.target.value); setMessage(""); setSaveError(""); }} />
            </label>
            </fieldset>
            <p className="detail-total" aria-live="polite">合計 <strong>{money(displayedTotal)}</strong></p>
            <p className="confirmed-total" aria-live="polite">保存済みの合計：{money(reservation.totalAmount)}</p>
            {!valid && users.length > 0 && <p className="error">品目と日数（1〜365の整数）を確認してください。金額は上限2,147,483,647円以内で入力してください。</p>}
            {saveError && <p className="error" role="alert">{saveError}</p>}
            <p role="status" className={!saving && message ? "success" : undefined}>{saving ? "保存中…" : message}</p>
            <div className="actions detail-actions">
              <button type="button" className="danger" disabled={saving} onClick={deleteReservation}>予約を削除</button>
              {!readOnly && <button type="button" disabled={saving || !valid} onClick={() => save()}>変更を保存</button>}
              {canCancel && <button type="button" className="cancel" disabled={saving || !valid} onClick={() => save("キャンセル")}>キャンセルする</button>}
              {nextAction && <button type="button" disabled={saving || !valid} onClick={() => save(nextAction.status)}>
                {nextAction.label}
              </button>}
            </div>
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
