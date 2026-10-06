/**
 * WiFi AutoLogin Pro - Backend Webhook Script
 * 
 * Target Sheets:
 * 1. "Total Install Count": Real-time installs & updates (1 row per device, 12-hr AM/PM IST time)
 * 2. "Feedback": Student feedback submissions (12-hr AM/PM IST time)
 * 
 * Setup:
 * 1. In your Google Sheet, click Extensions -> Apps Script
 * 2. Replace all existing code with this file
 * 3. Click Save (disk icon)
 * 4. Click Deploy -> Manage deployments -> Edit (pencil icon) -> Version: "New version" -> Click Deploy!
 */

function doPost(e) {
  try {
    var ss = SpreadsheetApp.getActiveSpreadsheet();
    var data = JSON.parse(e.postData.contents);
    var now = new Date();
    
    // 12-Hour format with AM/PM (e.g. "06-10-2026 06:15:30 PM")
    var defaultFormattedDate = Utilities.formatDate(now, "Asia/Kolkata", "dd-MM-yyyy hh:mm:ss a");
    var timestampStr = data.formattedTime || defaultFormattedDate;
    
    // ========================================================
    // ROUTE 1: Total Installs & Unique Devices (Total Install Count)
    // ========================================================
    if (data.type === "install" || data.event === "NEW_INSTALL" || data.event === "APP_UPDATE" || data.installId) {
      var installSheet = ss.getSheetByName("Total Install Count") || ss.getSheetByName("Sheet2");
      if (!installSheet) {
        installSheet = ss.insertSheet("Total Install Count");
      }
      
      // Setup headers if empty
      if (installSheet.getLastRow() === 0) {
        installSheet.appendRow(["Timestamp (12-hr IST)", "Install ID (UUID)", "Event Type", "Device Model", "Android Version", "App Version"]);
        installSheet.getRange(1, 1, 1, 6).setFontWeight("bold").setBackground("#E8F0FE");
      }
      
      var installId = data.installId || "";
      var existingRow = -1;
      
      // Check if this Device Install ID (UUID) already exists in Column B
      if (installId && installSheet.getLastRow() > 1) {
        var idValues = installSheet.getRange(2, 2, installSheet.getLastRow() - 1, 1).getValues();
        for (var i = 0; i < idValues.length; i++) {
          if (idValues[i][0] === installId) {
            existingRow = i + 2; // Row index
            break;
          }
        }
      }
      
      if (existingRow > 0) {
        // DEVICE ALREADY EXISTS -> Update existing row with new version & 12-hr timestamp!
        installSheet.getRange(existingRow, 1).setNumberFormat("@").setValue(timestampStr);
        installSheet.getRange(existingRow, 3).setValue(data.event || "APP_UPDATE");
        installSheet.getRange(existingRow, 4).setValue(data.deviceModel || "");
        installSheet.getRange(existingRow, 5).setValue(data.androidVersion || "");
        installSheet.getRange(existingRow, 6).setValue(data.appVersion || "3.0.0");
        
        return ContentService.createTextOutput(JSON.stringify({ status: "success", action: "updated", target: "Total Install Count" }))
          .setMimeType(ContentService.MimeType.JSON);
      } else {
        // NEW DEVICE -> Append new row with plain-text 12-hour timestamp
        installSheet.appendRow([
          timestampStr,
          installId,
          "NEW_INSTALL",
          data.deviceModel || "",
          data.androidVersion || "",
          data.appVersion || "3.0.0"
        ]);
        var lastRow = installSheet.getLastRow();
        installSheet.getRange(lastRow, 1).setNumberFormat("@").setValue(timestampStr);
        
        return ContentService.createTextOutput(JSON.stringify({ status: "success", action: "inserted", target: "Total Install Count" }))
          .setMimeType(ContentService.MimeType.JSON);
      }
    }
    
    // ========================================================
    // ROUTE 2: User In-App Feedback (Feedback tab)
    // ========================================================
    var feedbackSheet = ss.getSheetByName("Feedback") || ss.getSheetByName("Sheet1");
    if (!feedbackSheet) {
      feedbackSheet = ss.insertSheet("Feedback");
    }
    
    if (feedbackSheet.getLastRow() === 0) {
      feedbackSheet.appendRow(["Timestamp (12-hr IST)", "Name", "Contact Number", "Reaction", "Category", "Message", "Device Model", "Android Version", "App Version"]);
      feedbackSheet.getRange(1, 1, 1, 9).setFontWeight("bold").setBackground("#E6F4EA");
    }
    
    feedbackSheet.appendRow([
      timestampStr,
      data.name || "Anonymous",
      data.contact || "",
      data.reaction || "",
      data.category || "",
      data.message || "",
      data.deviceModel || "",
      data.androidVersion || "",
      data.appVersion || "3.0.0"
    ]);
    var feedbackLastRow = feedbackSheet.getLastRow();
    feedbackSheet.getRange(feedbackLastRow, 1).setNumberFormat("@").setValue(timestampStr);
    
    return ContentService.createTextOutput(JSON.stringify({ status: "success", route: "Feedback" }))
      .setMimeType(ContentService.MimeType.JSON);
      
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
