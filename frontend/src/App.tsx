import React from 'react';
import './App.css';
import {StudyList} from "./StudyList";
import {Link, Route, Routes} from "react-router-dom";
import {Study} from "./Study";

function App() {
    return (<>
            <Routes>
                <Route path="/" element={<StudyList/>}/>
                <Route path="/study/:number" element={<Study/>}/>

                <Route path="*" element={<div>
                    <h1>404</h1>
                    <p>Page not found</p>
                    <Link to="/">Go to home</Link>
                </div>}/>
            </Routes>
        </>
    );
}

export default App;
