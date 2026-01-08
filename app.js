// --- GLOBAL VARIABLES ---
const HISTORY_KEY = "shikshak_history_v1";
const API_STORAGE_KEY = "shikshak_gemini_key";
let apiKey = localStorage.getItem(API_STORAGE_KEY);
let currentLessonData = null; // Store text for saving

// --- INITIALIZATION ---
window.onload = function() {
    if (!apiKey) {
        document.getElementById('api-modal').classList.remove('hidden');
    }
    loadHistory();
};

// --- API KEY MANAGEMENT ---
window.saveApiKey = function() {
    const input = document.getElementById('api-input').value.trim();
    if (input.length > 10) {
        localStorage.setItem(API_STORAGE_KEY, input);
        apiKey = input;
        document.getElementById('api-modal').classList.add('hidden');
        alert("API Key Saved! You are ready.");
    } else {
        alert("Invalid API Key");
    }
}

window.clearKey = function() {
    if(confirm("Reset API Key?")) {
        localStorage.removeItem(API_STORAGE_KEY);
        location.reload();
    }
}

// --- CORE FUNCTIONS ---

// 1. Generate Logic
window.generatePlan = async function() {
    const grade = document.getElementById('grade').value;
    const unit = document.getElementById('unit').value;
    const topic = document.getElementById('topic').value;

    if (!topic) { alert("Please enter a Topic Name"); return; }

    // UI Loading State
    const btnText = document.getElementById('btn-text');
    const spinner = document.getElementById('loading-spinner');
    const btn = document.getElementById('btn-generate');
    
    btn.disabled = true;
    btnText.innerText = "कृपया पर्खनुहोस्...";
    spinner.classList.remove('hidden');

    try {
        const promptText = `
        Act as a Nepali Curriculum expert (CDC Nepal). 
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

        const response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=${apiKey}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                contents: [{ parts: [{ text: promptText }] }]
            })
        });

        const data = await response.json();
        
        if (data.error) throw new Error(data.error.message);
        
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

// 2. Save to History (Local Storage)
window.saveToHistory = function() {
    if (!currentLessonData) return;
    const history = JSON.parse(localStorage.getItem(HISTORY_KEY) || "[]");
    history.unshift(currentLessonData); // Add to top
    localStorage.setItem(HISTORY_KEY, JSON.stringify(history));
    alert("Saved to History tab!");
    loadHistory(); // Refresh tab
}

// 3. Export PDF with Custom Font (TTF)
window.exportToPdf = async function() {
    const btn = document.getElementById('pdf-btn');
    const oldText = btn.innerText;
    btn.innerText = "Generating PDF...";
    
    try {
        const { jsPDF } = window.jspdf;
        const doc = new jsPDF();

        // LOAD FONT FILE (Assuming file name is 'font.ttf')
        // We use fetch because in PWA we can access relative files
        const fontUrl = './font.ttf'; 
        const fontBytes = await fetch(fontUrl).then(res => {
            if (!res.ok) throw new Error("font.ttf not found in folder");
            return res.arrayBuffer();
        });

        // Convert to Base64 manually
        let binary = '';
        const bytes = new Uint8Array(fontBytes);
        for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        const fontBase64 = btoa(binary);

        // Add font to PDF VFS
        doc.addFileToVFS('MyNepaliFont.ttf', fontBase64);
        doc.addFont('MyNepaliFont.ttf', 'MyNepaliFont', 'normal');
        doc.setFont('MyNepaliFont');

        // Write Text
        doc.setFontSize(16);
        doc.text(`Lesson Plan: Class ${currentLessonData.grade}`, 10, 20);
        doc.setFontSize(12);
        
        // Clean markdown symbols for PDF clarity
        const cleanText = currentLessonData.content.replace(/\*\*/g, '').replace(/#/g, '');
        
        // Split text to fit page
        const splitText = doc.splitTextToSize(cleanText, 180);
        
        let y = 30;
        splitText.forEach(line => {
            if (y > 280) { doc.addPage(); y = 20; }
            doc.text(line, 10, y);
            y += 7;
        });

        doc.save(`${currentLessonData.topic}.pdf`);

    } catch (e) {
        alert("PDF Error: " + e.message + "\nMake sure 'font.ttf' is in your Acode folder!");
    } finally {
        btn.innerText = oldText;
    }
}

// --- UTILITIES ---
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
            <p class="text-xs text-gray-500">Class: ${item.grade} | Date: ${item.date}</p>
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
    const item = history[index];
    
    currentLessonData = item;
    document.getElementById('ai-output').innerHTML = marked.parse(item.content);
    document.getElementById('result-card').classList.remove('hidden');
    switchView('create'); // Go back to main view
}
