/**
 * WiFi AutoLogin Pro - Backend Webhook Script
 * 
 * Features:
 * 1. Sheet2: Installs & Updates (1 row per device, 12-hour AM/PM IST timestamp)
 * 2. Sheet1: Feedback submissions (Compulsory Name, 10-digit Phone, Message, 12-hour AM/PM IST timestamp)
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
    
    // ==========================================
    // ROUTE 1: Total Installs & Unique Devices (Sheet2)
    // ==========================================
    if (data.type === "install" || data.event === "NEW_INSTALL" || data.event === "APP_UPDATE" || data.installId) {
      var sheet2 = ss.getSheetByName("Sheet2");
      if (!sheet2) {
        sheet2 = ss.insertSheet("Sheet2");
      }
      
      // Setup headers if Sheet2 is empty
      if (sheet2.getLastRow() === 0) {
        sheet2.appendRow(["Timestamp (12-hr IST)", "Install ID (UUID)", "Event Type", "Device Model", "Android Version", "App Version"]);
        sheet2.getRange(1, 1, 1, 6).setFontWeight("bold").setBackground("#E8F0FE");
      }
      
      var installId = data.installId || "";
      var existingRow = -1;
      
      // Check if this Device Install ID (UUID) already exists in Column B
      if (installId && sheet2.getLastRow() > 1) {
        var idValues = sheet2.getRange(2, 2, sheet2.getLastRow() - 1, 1).getValues();
        for (var i = 0; i < idValues.length; i++) {
          if (idValues[i][0] === installId) {
            existingRow = i + 2; // Row index
            break;
          }
        }
      }
      
      if (existingRow > 0) {
        // DEVICE ALREADY EXISTS -> Update existing row with new version & 12-hr timestamp!
        sheet2.getRange(existingRow, 1).setNumberFormat("@").setValue(timestampStr);
        sheet2.getRange(existingRow, 3).setValue(data.event || "APP_UPDATE");
        sheet2.getRange(existingRow, 4).setValue(data.deviceModel || "");
        sheet2.getRange(existingRow, 5).setValue(data.androidVersion || "");
        sheet2.getRange(existingRow, 6).setValue(data.appVersion || "3.0.0");
        
        return ContentService.createTextOutput(JSON.stringify({ status: "success", action: "updated", row: existingRow }))
          .setMimeType(ContentService.MimeType.JSON);
      } else {
        // NEW DEVICE -> Append new row with plain-text 12-hour timestamp
        sheet2.appendRow([
          timestampStr,
          installId,
          "NEW_INSTALL",
          data.deviceModel || "",
          data.androidVersion || "",
          data.appVersion || "3.0.0"
        ]);
        var lastRow = sheet2.getLastRow();
        sheet2.getRange(lastRow, 1).setNumberFormat("@").setValue(timestampStr);
        
        return ContentService.createTextOutput(JSON.stringify({ status: "success", action: "inserted" }))
          .setMimeType(ContentService.MimeType.JSON);
      }
    }
    
    // ==========================================
    // ROUTE 2: User In-App Feedback (Sheet1)
    // ==========================================
    var sheet1 = ss.getSheetByName("Sheet1");
    if (!sheet1) {
      sheet1 = ss.insertSheet("Sheet1");
    }
    
    if (sheet1.getLastRow() === 0) {
      sheet1.appendRow(["Timestamp (12-hr IST)", "Name", "Contact Number", "Reaction", "Category", "Message", "Device Model", "Android Version", "App Version"]);
      sheet1.getRange(1, 1, 1, 9).setFontWeight("bold").setBackground("#E6F4EA");
    }
    
    sheet1.appendRow([
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
    var feedbackLastRow = sheet1.getLastRow();
    sheet1.getRange(feedbackLastRow, 1).setNumberFormat("@").setValue(timestampStr);
    
    return ContentService.createTextOutput(JSON.stringify({ status: "success", route: "Sheet1" }))
      .setMimeType(ContentService.MimeType.JSON);
      
  } catch (err) {
    return ContentService.createTextOutput(JSON.stringify({ status: "error", message: err.toString() }))
      .setMimeType(ContentService.MimeType.JSON);
  }
}
