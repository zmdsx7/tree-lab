let currentUser = null;
let registering = false;

function setCurrentUser(user) {
    currentUser = user;
    $('#accountName').textContent = user ? user.name : '';
    $('#accountButton').hidden = Boolean(user);
    $('#logoutButton').hidden = !user;
    $('#historyButton').textContent = user ? '◷ ประวัติของฉัน' : '◷ ประวัติ';
}

function showAuth(register = false) {
    registering = register;
    $('#authForm').reset();
    $('#authError').textContent = '';
    $('#nameField').hidden = !register;
    $('#confirmField').hidden = !register;
    $('#authName').required = register;
    $('#authConfirm').required = register;
    $('#authPassword').minLength = register ? 8 : 1;
    $('#authPassword').autocomplete = register ? 'new-password' : 'current-password';
    $('#authTitle').textContent = register ? 'สมัครสมาชิก' : 'เข้าสู่ระบบ';
    $('#authSubmit').textContent = register ? 'สมัครสมาชิก' : 'เข้าสู่ระบบ';
    $('#authSwitch').textContent = register ? 'มีบัญชีแล้ว? เข้าสู่ระบบ' : 'ยังไม่มีบัญชี? สมัครสมาชิก';
    if (!$('#authModal').open) $('#authModal').showModal();
}

async function requireAccount() {
    const data = await api('/auth/me');
    setCurrentUser(data.user);
    if (currentUser) return true;
    showAuth();
    return false;
}

$('#accountButton').onclick = () => showAuth();
$('#authClose').onclick = () => $('#authModal').close();
$('#authSwitch').onclick = () => showAuth(!registering);
$('#authForm').onsubmit = async event => {
    event.preventDefault();
    const password = $('#authPassword').value;
    if (registering && password !== $('#authConfirm').value) {
        $('#authError').textContent = 'รหัสผ่านทั้งสองช่องไม่ตรงกัน';
        return;
    }
    if (registering && new TextEncoder().encode(password).length > 72) {
        $('#authError').textContent = 'รหัสผ่านต้องไม่เกิน 72 ไบต์';
        return;
    }
    $('#authSubmit').disabled = true;
    $('#authSwitch').disabled = true;
    $('#authError').textContent = '';
    try {
        const data = await api(registering ? '/auth/register' : '/auth/login', 'POST', {
            name: $('#authName').value,
            email: $('#authEmail').value,
            password
        });
        setCurrentUser(data.user);
        $('#authForm').reset();
        $('#authModal').close();
        msg('เข้าสู่ระบบแล้ว สามารถบันทึกและเปิดประวัติของคุณได้');
    } catch (error) {
        $('#authError').textContent = error.message;
    } finally {
        $('#authSubmit').disabled = false;
        $('#authSwitch').disabled = false;
    }
};
$('#logoutButton').onclick = async () => {
    try {
        await api('/auth/logout', 'POST');
        setCurrentUser(null);
        // Remove previously loaded private history from the shared modal DOM.
        $('#modal').close();
        $('#modalBody').replaceChildren();
        actions = []; result = null; index = 0;
        $('#canvas').innerHTML = '<div class="empty">ออกจากระบบแล้ว · สร้างต้นไม้ใหม่เพื่อทดลองต่อได้</div>';
        $('#steps').replaceChildren(); $('#traversals').replaceChildren();
        $('#count').textContent = '0 โหนด'; $('#position').textContent = '0 / 0';
        $('#prev').disabled = true; $('#next').disabled = true;
        msg('ออกจากระบบแล้ว');
    } catch (error) { msg(error.message); }
};
api('/auth/me').then(data => setCurrentUser(data.user)).catch(() => {
    msg('ยังเชื่อมต่อระบบสมาชิกไม่ได้ ลองโหลดหน้าเว็บใหม่');
});
