const $ = selector => document.querySelector(selector);

let kind = 'bst';
let actions = [];
let result = null;
let index = 0;
let mode = 'single';

// แสดงข้อความแจ้งเตือน
const msg = text => {
    const toast = $('#toast');

    toast.textContent = text;
    toast.style.display = 'block';

    setTimeout(() => {
        toast.style.display = 'none';
    }, 3000);
};

// ติดต่อ Java Backend
async function api(path, method = 'GET', body) {
    const response = await fetch('/api' + path, {
        method,
        headers: {
            'Content-Type': 'application/json'
        },
        body: body === undefined
            ? undefined
            : JSON.stringify(body)
    });

    if (!response.ok) {
        const error = await response.json().catch(() => ({}));
        throw Error(error.message || 'เกิดข้อผิดพลาด');
    }

    return response.json();
}

// ตรวจสอบจำนวนเต็ม
function integer(text) {
    if (!/^-?\d+$/.test(text.trim())) {
        throw Error('กรุณากรอกจำนวนเต็มให้ถูกต้อง');
    }

    const number = Number(text);

    if (
        !Number.isSafeInteger(number) ||
        number < -2147483648 ||
        number > 2147483647
    ) {
        throw Error('ตัวเลขต้องอยู่ในช่วงจำนวนเต็ม 32 บิต');
    }

    return number;
}

// อัปเดตประเภทต้นไม้
function updateKind() {
    document.querySelectorAll('[data-kind]').forEach(button => {
        button.classList.toggle(
            'selected',
            button.dataset.kind === kind
        );
    });

    $('#title').textContent = kind === 'bst'
        ? 'Binary Search Tree'
        : 'AVL Tree';

    $('#subtitle').textContent = kind === 'bst'
        ? 'ต้นไม้ค้นหาแบบทวิภาค'
        : 'ต้นไม้ค้นหาที่ปรับสมดุลอัตโนมัติ';

    $('#kindInfo').textContent = kind === 'bst'
        ? 'ค่าน้อยอยู่ซ้าย ค่ามากอยู่ขวา โดยไม่มีการหมุนปรับสมดุล'
        : 'ตรวจสอบสมดุลหลังเพิ่มหรือลบ และหมุนโหนดเมื่อจำเป็น';
}

// นับจำนวนโหนด
function nodeCount(node) {
    if (!node) return 0;

    return 1 + nodeCount(node.left) + nodeCount(node.right);
}

// อ่านสถานะค้นหาจากขั้นตอนที่เลือก
function searchHighlight(step) {
    if (!step) return null;

    const text = step.text || '';

    const comparing = text.match(
        /^เปรียบเทียบ (-?\d+) กับ (-?\d+)$/
    );

    const found = text.match(/^พบ (-?\d+)$/);

    if (found) {
        return {
            value: Number(found[1]),
            found: true
        };
    }

    if (comparing) {
        return {
            value: Number(comparing[2]),
            found: false
        };
    }

    return null;
}

// สร้างภาพต้นไม้
function treeHTML(tree, step = null, treeKind = kind) {
    if (!tree) {
        return '<div class="empty">ยังไม่มีโหนดในต้นไม้</div>';
    }

    const nodes = [];
    const edges = [];

    let cursor = 0;
    let maxDepth = 0;

    const highlight = searchHighlight(step);

    function visit(node, depth) {
        if (!node) return null;

        const left = visit(node.left, depth + 1);

        const x = ++cursor * 75;
        const y = depth * 90 + 45;

        nodes.push({
            x,
            y,
            node
        });

        maxDepth = Math.max(maxDepth, depth);

        const right = visit(node.right, depth + 1);

        if (left) {
            edges.push([x, y, left.x, left.y]);
        }

        if (right) {
            edges.push([x, y, right.x, right.y]);
        }

        return { x, y };
    }

    visit(tree, 0);

    const width = Math.max(300, (cursor + 1) * 75);
    const height = (maxDepth + 1) * 90 + 20;

    const edgesHTML = edges.map(([x, y, childX, childY]) => `
        <line
            x1="${x}"
            y1="${y}"
            x2="${childX}"
            y2="${childY}"
            stroke="#b8cbb5"
            stroke-width="2"
        />
    `).join('');

    const nodesHTML = nodes.map(({ x, y, node }) => {
        let fill = '#e2efc9';
        let stroke = '#a2c57a';
        let textColor = '#254d3d';
        let strokeWidth = 1.5;
        let status = '';

        if (highlight && node.value === highlight.value) {
            strokeWidth = 3;

            if (highlight.found) {
                fill = '#206347';
                stroke = '#124b34';
                textColor = '#ffffff';
                status = `พบ ${node.value}`;
            } else {
                fill = '#ffe08a';
                stroke = '#d49a15';
                textColor = '#694800';
                status = `กำลังเปรียบเทียบกับ ${node.value}`;
            }
        }

        const fontSize = String(node.value).length > 5
            ? 9
            : 14;

        const balanceHTML = treeKind === 'avl'
            ? `
                <text
                    x="${x}"
                    y="${y + 38}"
                    text-anchor="middle"
                    font-size="9"
                    fill="#839881"
                >
                    BF ${node.balance}
                </text>
            `
            : '';

        return `
            <g>
                <title>${status || `โหนด ${node.value}`}</title>

                <circle
                    cx="${x}"
                    cy="${y}"
                    r="23"
                    fill="${fill}"
                    stroke="${stroke}"
                    stroke-width="${strokeWidth}"
                />

                <text
                    x="${x}"
                    y="${y + 5}"
                    text-anchor="middle"
                    font-size="${fontSize}"
                    font-weight="700"
                    fill="${textColor}"
                >
                    ${node.value}
                </text>

                ${balanceHTML}
            </g>
        `;
    }).join('');

    return `
        <svg
            viewBox="0 0 ${width} ${height}"
            style="width:${width}px;height:${height}px"
            role="img"
            aria-label="ภาพต้นไม้"
        >
            ${edgesHTML}
            ${nodesHTML}
        </svg>
    `;
}

// แสดงผลต้นไม้และขั้นตอน
function render() {
    if (!result) return;

    const steps = result.steps;

    index = Math.max(
        0,
        Math.min(index, steps.length - 1)
    );

    const currentStep = steps[index];

    const snapshot = steps.length
        ? currentStep.tree
        : result.tree;

    $('#canvas').innerHTML = treeHTML(
        snapshot,
        currentStep
    );

    $('#count').textContent = nodeCount(snapshot) + ' โหนด';

    $('#traversals').innerHTML = Object.entries(
        result.traversals
    ).map(([name, values]) => `
        <div>
            <strong>${name.toUpperCase()}</strong>
            &nbsp;
            ${values.join(' → ') || '—'}
        </div>
    `).join('');

    $('#traversals').title =
        'ลำดับการท่องจากต้นไม้ผลลัพธ์สุดท้าย';

    $('#steps').innerHTML = steps.length
        ? steps.map((step, stepIndex) => {
            // ใช้ display:none เพื่อซ่อนขั้นตอนอื่น
            // โดยไม่ต้องแก้ไข CSS เพิ่ม
            const hiddenStyle =
                mode === 'single' && stepIndex !== index
                    ? 'style="display:none"'
                    : '';

            return `
                <div
                    class="step ${stepIndex === index ? 'active' : ''}"
                    data-step="${stepIndex}"
                    ${hiddenStyle}
                >
                    <i>${stepIndex + 1}</i>
                    <span></span>
                </div>
            `;
        }).join('')
        : '<p class="hint">ยังไม่มีขั้นตอน</p>';

    document.querySelectorAll('[data-step]').forEach(element => {
        const stepIndex = Number(element.dataset.step);

        element.querySelector('span').textContent =
            steps[stepIndex].text;

        element.onclick = () => {
            index = stepIndex;
            render();
        };
    });

    $('#position').textContent = steps.length
        ? `${index + 1} / ${steps.length}`
        : '0 / 0';

    $('#prev').disabled =
        !steps.length || index === 0;

    $('#next').disabled =
        !steps.length || index === steps.length - 1;

    document.querySelectorAll('[data-mode]').forEach(button => {
        button.classList.toggle(
            'selected',
            button.dataset.mode === mode
        );
    });
}

// ส่งรายการคำสั่งไปคำนวณ
async function run(nextActions, atEnd = true) {
    if (nextActions.length > 200) {
        throw Error('รองรับสูงสุด 200 รายการ');
    }

    const response = await api('/calculate', 'POST', {
        kind,
        actions: nextActions
    });

    actions = nextActions;
    result = response;

    index = atEnd
        ? Math.max(0, response.steps.length - 1)
        : 0;

    render();
}

// เปลี่ยนประเภทต้นไม้
document.querySelectorAll('[data-kind]').forEach(button => {
    button.onclick = async () => {
        try {
            kind = button.dataset.kind;
            updateKind();

            if (actions.length) {
                await run(actions);
            }
        } catch (error) {
            msg(error.message);
        }
    };
});

// สร้างต้นไม้จากชุดตัวเลข
$('#create').onclick = async () => {
    try {
        const raw = $('#numbers').value.trim();

        if (!raw) {
            throw Error('กรุณากรอกตัวเลข');
        }

        const values = raw.split(',').map(integer);

        const initialActions = values.map(value => ({
            type: 'insert',
            value
        }));

        await run(initialActions, false);
    } catch (error) {
        msg(error.message);
    }
};

// เพิ่ม ลบ และค้นหา
const operationButtons = [
    ['insert', 'insert'],
    ['remove', 'delete'],
    ['search', 'search']
];

for (const [buttonId, type] of operationButtons) {
    $('#' + buttonId).onclick = async () => {
        try {
            const value = integer($('#number').value);
            const previousStepCount = result?.steps.length || 0;

            await run([
                ...actions,
                { type, value }
            ]);

            // เริ่มแสดงที่ขั้นแรกของคำสั่งล่าสุด
            index = previousStepCount;
            render();

            $('#number').value = '';
        } catch (error) {
            msg(error.message);
        }
    };
}

// ปุ่มย้อนกลับ
$('#prev').onclick = () => {
    index--;
    render();
};

// ปุ่มถัดไป
$('#next').onclick = () => {
    index++;
    render();
};

// เปลี่ยนโหมดแสดงขั้นตอน
document.querySelectorAll('[data-mode]').forEach(button => {
    button.onclick = () => {
        mode = button.dataset.mode;
        render();
    };
});

// ปิดหน้าต่าง
$('#close').onclick = () => {
    $('#modal').close();
};

// เปรียบเทียบ BST กับ AVL
$('#compare').onclick = async () => {
    try {
        if (!actions.length) {
            throw Error('กรุณาสร้างต้นไม้ก่อน');
        }

        const [bstResult, avlResult] = await Promise.all(
            ['bst', 'avl'].map(treeKind => {
                return api('/calculate', 'POST', {
                    kind: treeKind,
                    actions
                });
            })
        );

        $('#modalTitle').textContent = 'เปรียบเทียบ BST กับ AVL';

        const bstHTML = treeHTML(
            bstResult.tree,
            null,
            'bst'
        );

        const avlHTML = treeHTML(
            avlResult.tree,
            null,
            'avl'
        );

        const rotations = avlResult.steps.filter(step =>
            step.text.startsWith('หมุน')
        );

        $('#modalBody').innerHTML = `
            <p class="hint">
                คำนวณจากลำดับการเพิ่มและลบเดียวกัน
                · ความสูงนับจำนวนระดับ (ต้นไม้เดี่ยวสูง 1)
            </p>

            <div class="comparison">
                <div>
                    <h3>
                        BST · สูง ${bstResult.tree?.height || 0}
                    </h3>

                    <div class="canvas">
                        ${bstHTML}
                    </div>
                </div>

                <div>
                    <h3>
                        AVL · สูง ${avlResult.tree?.height || 0}
                    </h3>

                    <div class="canvas">
                        ${avlHTML}
                    </div>

                    <p class="hint">
                        หมุนทั้งหมด ${rotations.length} ครั้ง
                    </p>

                    <ol id="rotations"></ol>
                </div>
            </div>
        `;

        rotations.forEach(step => {
            const item = document.createElement('li');
            item.textContent = step.text;
            $('#rotations').append(item);
        });

        $('#modal').showModal();
    } catch (error) {
        msg(error.message);
    }
};

// บันทึกประวัติ
$('#save').onclick = async () => {
    try {
        if (!actions.length) {
            throw Error('กรุณาสร้างต้นไม้ก่อน');
        }

        const name = prompt(
            'ตั้งชื่อประวัติ',
            'ต้นไม้ของฉัน'
        );

        if (name === null) return;

        await api('/history', 'POST', {
            name,
            request: {
                kind,
                actions
            }
        });

        msg('บันทึกเรียบร้อยแล้ว');
    } catch (error) {
        msg(error.message);
    }
};

// เปิดประวัติ
$('#historyButton').onclick = async () => {
    try {
        const history = await api('/history');

        $('#modalTitle').textContent = 'ประวัติที่บันทึก';

        $('#modalBody').innerHTML = history.length
            ? ''
            : '<p class="hint">ยังไม่มีประวัติที่บันทึก</p>';

        history.forEach(item => {
            const row = document.createElement('div');
            row.className = 'history-row';

            const label = document.createElement('span');
            label.textContent =
                `${item.name} · ${item.created_at} UTC`;

            const openButton = document.createElement('button');
            openButton.textContent = 'เปิด';

            openButton.onclick = async () => {
                try {
                    const data = await api(
                        '/history/' + item.id
                    );

                    kind = data.kind;
                    updateKind();

                    await run(data.actions);

                    $('#modal').close();
                } catch (error) {
                    msg(error.message);
                }
            };

            const deleteButton = document.createElement('button');
            deleteButton.textContent = 'ลบ';

            deleteButton.onclick = async () => {
                try {
                    if (!confirm('ลบประวัตินี้หรือไม่?')) {
                        return;
                    }

                    await api(
                        '/history/' + item.id,
                        'DELETE'
                    );

                    row.remove();
                } catch (error) {
                    msg(error.message);
                }
            };

            row.append(
                label,
                openButton,
                deleteButton
            );

            $('#modalBody').append(row);
        });

        $('#modal').showModal();
    } catch (error) {
        msg(error.message);
    }
};

// ตั้งค่าหน้าเว็บครั้งแรก
updateKind();