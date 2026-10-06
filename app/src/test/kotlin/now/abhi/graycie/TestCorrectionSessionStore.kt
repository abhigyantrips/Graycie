package now.abhi.graycie

internal class TestCorrectionSessionStore : CorrectionSessionStore {
    var session: CorrectionSession? = null
    var accepts = true
    override fun read() = session
    override fun write(session: CorrectionSession): Boolean {
        if (!accepts) return false
        this.session = session
        return true
    }
}
