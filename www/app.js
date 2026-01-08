// --- GLOBAL CONFIG ---
const HISTORY_KEY = "shikshak_history_v1";
const API_STORAGE_KEY = "shikshak_gemini_key";
let currentLessonData = null; // Store text for saving

// --- INITIALIZATION ---
window.onload = function() {
    const storedKey = localStorage.getItem(API_STORAGE_KEY);
    if (!storedKey) {
        document.getElementById('api-modal').classList.remove('hidden');
    }
    loadHistory();
};

// --- API KEY MANAGEMENT ---
window.saveApiKey = function() {
    const input = document.getElementById('api-input').value.trim();
    if (input.length > 20) {
        localStorage.setItem(API_STORAGE_KEY, input);
        document.getElementById('api-modal').classList.add('hidden');
        alert("Success! API Key saved.");
    } else {
        alert("Key appears too short. Please copy the full key.");
    }
}

window.clearKey = function() {
    if(confirm("Do you want to remove the API Key?")) {
        localStorage.removeItem(API_STORAGE_KEY);
        location.reload(); 
    }
}

// --- CORE FUNCTIONS ---

window.generatePlan = async function() {
    // 1. GET KEY FRESH FROM STORAGE
    const apiKey = localStorage.getItem(API_STORAGE_KEY);
    
    if (!apiKey) {
        document.getElementById('api-modal').classList.remove('hidden');
        return;
    }

    const grade = document.getElementById('grade').value;
    const unit = document.getElementById('unit').value || "Unit 1";
    const topic = document.getElementById('topic').value;

    if (!topic) { 
        alert("कृपया पाठको विषय (Topic) लेख्नुहोस्।"); 
        return; 
    }

    // UI Loading State
    const btnText = document.getElementById('btn-text');
    const spinner = document.getElementById('loading-spinner');
    const btn = document.getElementById('btn-generate');
    
    btn.disabled = true;
    btnText.innerText = "कृपया पर्खनुहोस्...";
    spinner.classList.remove('hidden');

    try {
        const promptText = `
        Act as a Nepali Curriculum expert. 
        Create a Lesson plan for Class: ${grade}, Unit: ${unit}, Topic: ${topic}.
        Language: Nepali (Devanagari Only).
        Format strict Markdown:
        # ${topic}
        **विशिष्ट उद्देश्य:**
        - [List objectives]
        **शैक्षिक सामग्री:**
        - [List materials]
        **शिक्षण सिकाई क्रियाकलाप:**
        - [Activities in detail]
        **मूल्यांकन:**
        - [Questions]
        **गृहकार्य:**
        - [Homework]
        `;

        // --- FIX IS HERE: CHANGED MODEL NAME TO 'gemini-1.5-flash-001' ---
        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash-001:generateContent?key=${apiKey}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                contents: [{ parts: [{ text: promptText }] }]
            })
        });

        const data = await response.json();
        
        // ERROR HANDLING
        if (!response.ok) {
            console.error(data);
            throw new Error(data.error?.message || "Invalid API Key or Model Name");
        }
        
        const rawText = data.candidates[0].content.parts[0].text;
        
        // Save temporary state
        currentLessonData = { 
            id: Date.now(),
            grade, unit, topic, 
            content: rawText,
            date: new Date().toLocaleDateString('ne-NP')
        };

        // Render with Markdown
        document.getElementById('ai-output').innerHTML = marked.parse(rawText);
        document.getElementById('result-card').classList.remove('hidden');

    } catch (error) {
        alert("Error: " + error.message);
    } finally {
        // Reset UI
        btn.disabled = false;
        btnText.innerText = "पाठ योजना तयार गर्नुहोस्";
        spinner.classList.add('hidden');
    }
}

// --- HISTORY & PDF UTILITIES ---

window.saveToHistory = function() {
    if (!currentLessonData) return;
    const history = JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]");
    history.unshift(currentLessonData); 
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    alert("Saved to History tab!");
    loadHistory(); 
}

window.exportToPdf = async function() {
    if (!currentLessonData) return;
    const btn = document.getElementById('pdf-btn');
    const oldText = btn.innerText;
    btn.innerText = "Processing...";
    
    try {
        const { jsPDF } = window.jspdf;
        const doc = new jsPDF();

        const fontUrl = './font.ttf'; 
        
        try {
            const fontBytes = await fetch(fontUrl).then(res => {
                if (!res.ok) throw new Error("font.ttf missing");
                return res.arrayBuffer();
            });

            const binary = new Uint8Array(fontBytes).reduce((data, byte) => data + String.fromCharCode(byte), '');
            const fontBase64 = btoa(binary);

            doc.addFileToVFS('Nepali.ttf', fontBase64);
            doc.addFont('Nepali.ttf', 'Nepali', 'normal');
            doc.setFont('Nepali');
        } catch (e) {
            console.warn("Font load failed");
        }

        doc.setFontSize(16);
        doc.text(`Lesson Plan: ${currentLessonData.topic}`, 10, 20);
        
        doc.setFontSize(12);
        const cleanText = currentLessonData.content.replace(/[#*]/g, '');
        const lines = doc.splitTextToSize(cleanText, 180);
        
        let y = 30;
        lines.forEach(line => {
            if (y > 280) { doc.addPage(); y = 20; }
            doc.text(line, 10, y);
            y += 7;
        });

        doc.save(`${currentLessonData.topic}.pdf`);

    } catch (e) {
        alert("PDF Error: " + e.message);
    } finally {
        btn.innerText = oldText;
    }
}

// --- NAVIGATION ---

window.switchView = function(viewName) {
    const create = document.getElementById('view-create');
    const history = document.getElementById('view-history');
    const navC = document.getElementById('nav-create');
    const navH = document.getElementById('nav-history');

    if (viewName === 'create') {
        create.classList.remove('hidden');
        history.classList.add('hidden');
        navC.classList.add('active');
        navH.classList.remove('active');
    } else {
        create.classList.add('hidden');
        history.classList.remove('hidden');
        navC.classList.remove('active');
        navH.classList.add('active');
        loadHistory();
    }
}

window.loadHistory = function() {
    const list = document.getElementById('history-list');
    const history = JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]");
    
    if (history.length === 0) {
        list.innerHTML = `<p class="text-center text-gray-400 mt-10">कुनै रेकर्ड फेला परेन।</p>`;
        return;
    }

    list.innerHTML = history.map((item, index) => `
        <div class="bg-white p-4 rounded shadow border-l-4 border-blue-600 relative">
            <h3 class="font-bold text-gray-800">${item.topic}</h3>
            <p class="text-xs text-gray-500">Class: ${item.grade} | ${item.date}</p>
            <div class="absolute top-4 right-4 flex gap-2">
                <button onclick="deleteItem(${index})" class="text-red-500 text-xs border border-red-200 p-1 rounded">Delete</button>
            </div>
            <button onclick="loadFromHistory(${index})" class="mt-2 text-blue-600 text-xs font-bold w-full text-left">View Plan</button>
        </div>
    `).join('');
}

window.deleteItem = function(index) {
    if(!confirm("Delete this plan?")) return;
    const history = JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]");
    history.splice(index, 1);
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    loadHistory();
}

window.loadFromHistory = function(index) {
    const history = JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]");
    currentLessonData = history[index];
    document.getElementById('ai-output').innerHTML = marked.parse(currentLessonData.content);
    document.getElementById('result-card').classList.remove('hidden');
    switchView('create');
                                 }
