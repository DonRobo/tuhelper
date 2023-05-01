import React from "react";
import {Study, TestControllerService} from "./generated";

export function StudyList() {
    const [studies, setStudies] = React.useState([] as Study[]);
    const [selectedType, setSelectedType] = React.useState<Study.type | null>(null);

    React.useEffect(() => {
        TestControllerService.studies().then(studies => {
            setStudies(studies);
        });
    }, []);

    const filteredStudies = selectedType
        ? studies.filter((study) => study.type === selectedType)
        : studies;

    const studyTypes = Object.values(Study.type);

    return (
        <div>
            <h1>Studies</h1>
            <div>
                Filter by type:
                <select
                    value={selectedType || ''}
                    onChange={(event) =>
                        setSelectedType(event.target.value as Study.type)
                    }
                >
                    <option value="">All</option>
                    {studyTypes.map((type) => (
                        <option key={type} value={type}>
                            {type}
                        </option>
                    ))}
                </select>
            </div>
            <table>
                <thead>
                <tr>
                    <th>Number</th>
                    <th>Name</th>
                    <th>Type</th>
                    <th>ECTS</th>
                </tr>
                </thead>
                <tbody>
                {filteredStudies.map((study) => (
                    <tr key={study.number}>
                        <td>{study.number}</td>
                        <td>{study.name}</td>
                        <td>{study.type}</td>
                        <td>{study.ects}</td>
                    </tr>
                ))}
                </tbody>
            </table>
        </div>
    );
}
