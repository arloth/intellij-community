/**
Some documentation

 * @receiver Some int
 * @param b String
 * @return Return [a] and nothing else
 */
fun Int.testMethod(b: String) {

}

fun test() {
    1.<caret>testMethod("value")
}

//INFO: <div class='definition'><pre><span style="color:#000080;font-weight:bold;">public</span> <span style="color:#000080;font-weight:bold;">fun</span> <span style="color:#000000;">Int</span><span style="">.</span><span style="color:#000000;">testMethod</span>(
//INFO:     <span style="color:#000000;">b</span><span style="">: </span><span style="color:#000000;">String</span>
//INFO: )<span style="">: </span><span style="color:#000000;">Unit</span></pre></div><div class='content'><p>Some documentation</p></div><table class='sections'><tr><td valign='top' class='section'><p>Receiver:</td><td valign='top'>Some int</td><tr><td valign='top' class='section'><p>Params:</td><td valign='top'><p><code><a href="psi_element://b"><code style='font-size:96%;'><span style="">b</span></code></a></code> - String</td><tr><td valign='top' class='section'><p>Returns:</td><td valign='top'>Return <a href="psi_element://a"><code style='font-size:96%;'><span style="color:#000000;">a</span></code></a> and nothing else</td></table>
