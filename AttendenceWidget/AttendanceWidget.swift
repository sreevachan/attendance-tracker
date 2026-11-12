import WidgetKit
import SwiftUI

// Must match the App Group ID in WebView.swift.
let widgetGroup = "group.sreevachan.attendance"

struct Entry: TimelineEntry {
    let date: Date
    let pct: Double
    let a: Int
    let c: Int
}

struct Provider: TimelineProvider {
    func load() -> Entry {
        let s = UserDefaults(suiteName: widgetGroup)
        return Entry(date: .now, pct: s?.double(forKey: "pct") ?? 0,
                     a: s?.integer(forKey: "a") ?? 0, c: s?.integer(forKey: "c") ?? 0)
    }
    func placeholder(in context: Context) -> Entry { Entry(date: .now, pct: 75, a: 3, c: 4) }
    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) { completion(load()) }
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        completion(Timeline(entries: [load()], policy: .never))
    }
}

struct AttendanceWidgetView: View {
    let e: Entry
    var color: Color { e.pct >= 75 ? .green : .red }
    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Attendance").font(.caption).foregroundStyle(.secondary)
            Text(String(format: "%.1f%%", e.pct))
                .font(.system(size: 36, weight: .heavy)).foregroundStyle(color)
            ProgressView(value: min(max(e.pct, 0), 100), total: 100).tint(color)
            Text("\(e.a) / \(e.c) classes").font(.caption2).foregroundStyle(.secondary)
        }
        .containerBackground(.background, for: .widget)
    }
}

@main
struct AttendanceWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "AttendanceWidget", provider: Provider()) { AttendanceWidgetView(e: $0) }
            .configurationDisplayName("Attendance")
            .description("Your attendance percentage and progress bar.")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}
