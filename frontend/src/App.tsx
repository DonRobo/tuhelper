import React from 'react';
import './App.css';
import {Study, TestControllerService} from "./generated";

function StudyList() {
    const [studies, setStudies] = React.useState([] as Study[]);

    React.useEffect(() => {
        TestControllerService.studies().then(studies => {
            setStudies(studies);
        });
    }, []);

    return <div>
        <h1>Studies</h1>
        <ul>
            {studies.map(study => <li key={study.number}>{study.name}</li>)}
        </ul>
    </div>;
}

function App() {
    return (
        <div>
            <StudyList/>
        </div>
    );
}

export default App;
