export default function PrivacyNotice() {
  return (
    <aside className="notice" aria-label="Privacy notice">
      <strong>Privacy:</strong> logs are analyzed by the backend running on your own machine and are not
      stored or sent to any third-party service. Logs can contain passwords, tokens, e-mail addresses and
      IP addresses. Masking of obvious secrets is best-effort and cannot guarantee that every secret is hidden.
    </aside>
  );
}
